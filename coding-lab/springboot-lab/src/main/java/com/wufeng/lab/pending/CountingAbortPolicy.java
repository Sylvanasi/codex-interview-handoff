package com.wufeng.lab.pending;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/** 拒绝时计数并抛异常，由聚合层转换为局部 REJECTED，避免请求线程执行上游调用。 */
public class CountingAbortPolicy implements RejectedExecutionHandler {
    private final AtomicLong rejectedCount = new AtomicLong();
    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejectedCount.incrementAndGet();
        throw new java.util.concurrent.RejectedExecutionException("pending executor saturated or shut down");
    }
    public long rejectedCount() { return rejectedCount.get(); }
}
