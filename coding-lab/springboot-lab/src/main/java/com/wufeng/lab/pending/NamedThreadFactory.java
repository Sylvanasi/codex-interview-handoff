package com.wufeng.lab.pending;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 给线程起有意义的名字。
 *
 * 为什么值得单独写一个类？因为线上排查全靠线程名：
 * 默认线程池产出的线程叫 pool-1-thread-3，一个应用里几十个池子，
 * 你在线程栈和日志里根本认不出哪条是"待办聚合"的线程。
 * 命名成 pending-1、pending-2，一眼就能定位。
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean daemon;
    private final AtomicInteger seq = new AtomicInteger(1);

    public NamedThreadFactory(String prefix) {
        this(prefix, true);
    }

    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + seq.getAndIncrement());
        // daemon=true：应用关闭时这些线程不该拦住 JVM 退出
        thread.setDaemon(daemon);
        return thread;
    }
}
