package com.wufeng.lab.pending;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义拒绝策略：既保留 CallerRuns 的语义，又把"被拒绝过几次"记下来。
 *
 * 【为什么不用默认的 AbortPolicy】
 * AbortPolicy 直接抛 RejectedExecutionException。在门户聚合这种场景下，
 * 一个上游把线程池打满就会让整页 500。CallerRunsPolicy 改成"谁提交谁自己跑"：
 * 任务不丢，但提交它的那个线程（也就是处理 HTTP 请求的 Tomcat 线程）会被占用，
 * 这本身就是一种天然的背压 —— 请求变慢，但服务不会崩。
 *
 * 【为什么还要自己写一个】
 * ThreadPoolExecutor 没有暴露"被拒绝了多少次"的计数器，
 * 而这恰恰是最该告警的指标：拒绝次数一涨，说明容量不够或下游在拖后腿。
 */
public class CountingCallerRunsPolicy implements RejectedExecutionHandler {

    private final AtomicLong rejectedCount = new AtomicLong();

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejectedCount.incrementAndGet();
        if (!executor.isShutdown()) {
            task.run();
        }
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }
}
