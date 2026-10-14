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
import java.util.concurrent.RejectedExecutionException;

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
 * 1. 线程池参数怎么定     → corePoolSize=4 是单请求实验起点，生产需按并发量与下游容量验证，队列必须有界
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
    private final CountingAbortPolicy rejectionPolicy;

    public PendingAggregationService(ThreadPoolExecutor pendingExecutor,
                                     @Qualifier("pendingRestTemplate") RestTemplate pendingRestTemplate,
                                     PendingProperties props,
                                     CountingAbortPolicy rejectionPolicy) {
        this.executor = pendingExecutor;
        this.restTemplate = pendingRestTemplate;
        this.props = props;
        this.rejectionPolicy = rejectionPolicy;
    }

    /** 聚合入口：parallel=true 走并发，false 走串行（用来对比耗时）。 */
    public Map<String, Object> aggregate(boolean parallel) {
        long startNanos = System.nanoTime();
        List<Map<String, Object>> systems = parallel ? aggregateParallel(startNanos) : aggregateSerial();
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

    private List<Map<String, Object>> aggregateParallel(long startNanos) {
        long deadline = startNanos + TimeUnit.MILLISECONDS.toNanos(props.getTimeoutMillis());
        Map<String, Future<List<PendingItem>>> futures = new LinkedHashMap<>();
        Map<String, Map<String, Object>> immediate = new LinkedHashMap<>();
        for (String system : props.getSystems()) {
            if (Thread.currentThread().isInterrupted()) {
                immediate.put(system, entry(system, "INTERRUPTED", List.of(), elapsed(startNanos), "调用线程被中断"));
            } else if (System.nanoTime() >= deadline) {
                immediate.put(system, entry(system, "TIMEOUT", List.of(), elapsed(startNanos), "提交前预算已耗尽"));
            } else {
                try {
                    futures.put(system, executor.submit(() -> fetch(system)));
                } catch (RejectedExecutionException e) {
                    immediate.put(system, entry(system, "REJECTED", List.of(), elapsed(startNanos), "线程池已满或关闭"));
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            for (String system : props.getSystems()) {
                if (immediate.containsKey(system)) {
                    out.add(immediate.get(system));
                    continue;
                }
                Future<List<PendingItem>> future = futures.get(system);
                String state = "OK";
                String note = null;
                List<PendingItem> items = List.of();
                try {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    long remaining = deadline - System.nanoTime();
                    // 已完成的 Future 不再等待，即使预算已耗尽也保留结果。
                    if (future.isDone()) items = future.get();
                    else if (remaining > 0) items = future.get(remaining, TimeUnit.NANOSECONDS);
                    else throw new TimeoutException();
                } catch (TimeoutException e) {
                    future.cancel(true);
                    state = "TIMEOUT";
                    note = "超过聚合时间预算；取消不保证阻塞 HTTP 立即停止";
                } catch (ExecutionException e) {
                    state = "ERROR";
                    note = e.getCause().getClass().getSimpleName();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    futures.values().forEach(f -> { if (!f.isDone()) f.cancel(true); });
                    state = "INTERRUPTED";
                    note = "调用线程被中断，已取消剩余任务";
                }
                out.add(entry(system, state, items, elapsed(startNanos), note));
            }
        } finally {
            futures.values().forEach(f -> { if (!f.isDone()) f.cancel(true); });
            // 清理尚未执行的取消任务，减少过期任务占用队列。
            executor.purge();
        }
        return out;
    }

    private long elapsed(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
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

    /** submit 把异常保存在 Future 中，get 时抛 ExecutionException；execute 的异常可到达未捕获异常处理器。 */
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
