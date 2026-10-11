package com.wufeng.lab.pending;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 统一待办聚合：串行 vs 并发的对照实验。
 *
 * 【业务问题】
 * 门户首页要展示 4 个系统的待办。串行调用是 4 次 HTTP 首尾相接，
 * 总耗时 = 各上游耗时之和；任一上游抖动，整页跟着卡。
 * 并发之后总耗时 ≈ 最慢的那个上游，抖动被超时和降级挡住。
 *
 * 【这段代码要能回答面试四连问】
 * 1. 线程池参数怎么定     → corePoolSize 按上游数量给（IO 密集），队列必须有界
 * 2. 超时怎么处理         → Future.get(timeout)，而且是"整页时间预算"，不是每个上游各等一遍
 * 3. 超时之后任务停了吗   → 见下面的注释，cancel(true) 对阻塞 IO 不保证立刻生效
 * 4. 一个上游挂了怎么办   → 只降级该上游，其余照常返回，绝不整页失败
 */
@Service
public class PendingAggregationService {

    private static final Logger log = LoggerFactory.getLogger(PendingAggregationService.class);

    private final ThreadPoolExecutor executor;
    private final RestTemplate restTemplate;
    private final PendingProperties props;
    private final CountingCallerRunsPolicy rejectionPolicy;

    public PendingAggregationService(ThreadPoolExecutor pendingExecutor,
                                     @Qualifier("pendingRestTemplate") RestTemplate pendingRestTemplate,
                                     PendingProperties props,
                                     CountingCallerRunsPolicy rejectionPolicy) {
        this.executor = pendingExecutor;
        this.restTemplate = pendingRestTemplate;
        this.props = props;
        this.rejectionPolicy = rejectionPolicy;
    }

    /** 聚合入口：parallel=true 走并发，false 走串行（用来对比耗时）。 */
    public Map<String, Object> aggregate(boolean parallel) {
        long startNanos = System.nanoTime();
        List<Map<String, Object>> systems = parallel ? aggregateParallel() : aggregateSerial();
        long totalMillis = (System.nanoTime() - startNanos) / 1_000_000;

        List<PendingItem> all = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        for (Map<String, Object> one : systems) {
            @SuppressWarnings("unchecked")
            List<PendingItem> items = (List<PendingItem>) one.get("items");
            all.addAll(items);
            if (!"OK".equals(one.get("state"))) {
                degraded.add((String) one.get("system"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", parallel ? "parallel" : "serial");
        result.put("timeoutMillis", props.getTimeoutMillis());
        result.put("totalCostMillis", totalMillis);
        result.put("itemCount", all.size());
        result.put("degradedSystems", degraded);
        result.put("systems", systems);
        result.put("items", all);
        return result;
    }

    // ==================== 并发版 ====================

    private List<Map<String, Object>> aggregateParallel() {
        List<String> systems = props.getSystems();
        long budgetNanos = TimeUnit.MILLISECONDS.toNanos(props.getTimeoutMillis());

        // ① 先把所有上游任务提交出去。提交本身很快、不阻塞 —— 关键就在这里：
        //    四个 HTTP 请求是同时在飞的，而不是一个接一个。
        Map<String, Future<List<PendingItem>>> futures = new LinkedHashMap<>();
        Map<String, Long> submittedAt = new LinkedHashMap<>();
        for (String system : systems) {
            submittedAt.put(system, System.currentTimeMillis());
            Callable<List<PendingItem>> task = () -> fetch(system);
            futures.put(system, executor.submit(task));
        }

        // ② 再按"整页时间预算"逐个收结果。
        //    如果每个上游各等一次 timeout，最坏就是 4 x timeout，
        //    "整页 250ms 出结果"这句话就是假的 —— 所以这里用统一的 deadline。
        long deadline = System.nanoTime() + budgetNanos;
        List<Map<String, Object>> out = new ArrayList<>();
        for (String system : systems) {
            Future<List<PendingItem>> future = futures.get(system);
            long begin = submittedAt.get(system);
            long remaining = deadline - System.nanoTime();
            try {
                if (remaining <= 0) {
                    future.cancel(true);
                    out.add(entry(system, "TIMEOUT", List.of(), cost(begin), "整页时间预算已耗尽，未再等待"));
                    continue;
                }
                List<PendingItem> items = future.get(remaining, TimeUnit.NANOSECONDS);
                out.add(entry(system, "OK", items, cost(begin), null));
            } catch (TimeoutException e) {
                // cancel(true) 发的是"中断信号"。RestTemplate 的读操作是阻塞在 socket 上的，
                // 中断并不能保证立刻把它叫停 —— 它可能还要等到读超时（上面配的 1 秒）才退出。
                // 这正是 pending-hub 里"跑完了终端还停几秒才退出"的原因。
                boolean cancelled = future.cancel(true);
                log.warn("上游 {} 超时降级：超过预算 {}ms 未返回，cancel={}",
                        system, props.getTimeoutMillis(), cancelled);
                out.add(entry(system, "TIMEOUT", List.of(), cost(begin), "超过时间预算未返回"));
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                log.warn("上游 {} 调用失败，降级：{}", system, cause.toString());
                out.add(entry(system, "ERROR", List.of(), cost(begin), cause.getClass().getSimpleName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.add(entry(system, "INTERRUPTED", List.of(), cost(begin), "收取结果的线程被中断"));
            }
        }
        return out;
    }

    // ==================== 串行版（对照基线） ====================

    private List<Map<String, Object>> aggregateSerial() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String system : props.getSystems()) {
            long begin = System.currentTimeMillis();
            try {
                out.add(entry(system, "OK", fetch(system), cost(begin), null));
            } catch (RuntimeException e) {
                out.add(entry(system, "ERROR", List.of(), cost(begin), e.getClass().getSimpleName()));
            }
        }
        return out;
    }

    // ==================== 工具 ====================

    /**
     * 真正交给线程池执行的任务体：调一个上游的待办接口。
     *
     * 【注意异常是怎么冒出来的】
     * 任务里抛出的异常不会走到线程的 UncaughtExceptionHandler，
     * 而是被 ThreadPoolExecutor 收进 Future，只有调用 get() 时才包成 ExecutionException 抛出。
     * 所以用 execute() 提交、又不拿返回值的任务，异常会被静默吞掉 —— 这是排查线上问题的常见大坑。
     */
    private List<PendingItem> fetch(String system) {
        PendingItem[] items = restTemplate.getForObject(
                props.getBaseUrl() + "/{system}", PendingItem[].class, system);
        return items == null ? List.of() : List.of(items);
    }

    private long cost(long beginMillis) {
        return System.currentTimeMillis() - beginMillis;
    }

    private Map<String, Object> entry(String system, String state, List<PendingItem> items,
                                      long costMillis, String note) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("system", system);
        one.put("state", state);
        one.put("costMillis", costMillis);
        one.put("count", items.size());
        one.put("items", items);
        if (note != null) {
            one.put("note", note);
        }
        return one;
    }

    /**
     * 线程池实时状态。
     *
     * 面试点：线程池不是"配完就不管了"。这几个数字就是监控要盯的东西：
     *   activeCount        正在干活的线程数 —— 长期等于 maxPoolSize 说明不够用
     *   queueSize          排队任务数    —— 队列持续不为空，说明下游慢或线程太少
     *   completedTaskCount 已完成任务数
     *   largestPoolSize    历史峰值线程数 —— 用来回看"当时到底开了多少线程"
     *   rejectedCount      被拒绝次数    —— 大于 0 就该告警，说明容量或下游出了问题
     * 生产上这些要打到 Micrometer / Prometheus，配线程池活跃度告警。
     */
    public Map<String, Object> poolSnapshot() {
        int queueSize = executor.getQueue().size();
        int queueCapacity = queueSize + executor.getQueue().remainingCapacity();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("corePoolSize", executor.getCorePoolSize());
        snapshot.put("maxPoolSize", executor.getMaximumPoolSize());
        snapshot.put("poolSize", executor.getPoolSize());
        snapshot.put("activeCount", executor.getActiveCount());
        snapshot.put("queueSize", queueSize);
        snapshot.put("queueCapacity", queueCapacity);
        snapshot.put("completedTaskCount", executor.getCompletedTaskCount());
        snapshot.put("totalTaskCount", executor.getTaskCount());
        snapshot.put("largestPoolSize", executor.getLargestPoolSize());
        snapshot.put("rejectedCount", rejectionPolicy.rejectedCount());
        snapshot.put("isShutdown", executor.isShutdown());
        return snapshot;
    }
}
