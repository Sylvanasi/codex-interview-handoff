package com.wufeng.lab.pending;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 待办聚合用到的两个基础设施 Bean：线程池 + 专用 RestTemplate。
 *
 * 【面试点一：为什么不直接用 Executors.newFixedThreadPool】
 * 它底层是无界的 LinkedBlockingQueue，队列永远不满 → maximumPoolSize 失效 →
 * 任务无限堆积 → OOM。更糟的是这个错要等线上出事故才暴露，
 * 所以阿里规约直接禁止用 Executors 创建线程池。必须手写 7 个参数。
 *
 * 【面试点二：为什么线程池要做成单例 Bean，还写 destroyMethod = "shutdown"】
 * 线程池是有状态资源（有队列、有线程），不能每次请求 new 一个 ——
 * 那等于没有池化，还每秒创建销毁上百个线程。
 * 做成单例 Bean 交给容器管生命周期，再加上 destroyMethod，
 * 应用关闭时会调 shutdown()：不再接新任务、把已提交的跑完，避免线程泄漏。
 */
@Configuration
public class PendingConfig {

    @Bean
    public CountingCallerRunsPolicy pendingRejectionPolicy() {
        return new CountingCallerRunsPolicy();
    }

    /**
     * 手写 ThreadPoolExecutor 的 7 个参数，一个都不省：
     *   1 corePoolSize      核心线程数
     *   2 maximumPoolSize   最大线程数
     *   3 keepAliveTime     非核心线程空闲存活时间
     *   4 unit              上面时间的单位
     *   5 workQueue         工作队列（这里是有界 ArrayBlockingQueue）
     *   6 threadFactory     线程工厂（这里给线程起名）
     *   7 handler           拒绝策略（这里自定义 CallerRuns + 计数）
     *
     * 执行顺序也要背下来：**核心线程 → 队列 → 非核心线程 → 拒绝**。
     * 注意"队列满了才开非核心线程"这一点，很多人会答成"核心满了就开非核心"。
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor pendingExecutor(PendingProperties props,
                                              CountingCallerRunsPolicy rejectionPolicy) {
        return new ThreadPoolExecutor(
                props.getCorePoolSize(),
                props.getMaxPoolSize(),
                props.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.getQueueCapacity()),
                new NamedThreadFactory("pending-"),
                rejectionPolicy);
    }

    /**
     * 给待办聚合单独一个 RestTemplate，而不是复用调金蝶那个。
     *
     * 【为什么要分开】超时值必须按调用方定：
     * 调金蝶推合同是"重操作"，读超时给到分钟级没关系；
     * 聚合门户是"用户在等页面"，读超时必须压到几百毫秒。
     * 共用一个 RestTemplate 只能取折中值，两边都不合适。
     *
     * 【为什么一定要设读超时】不设的话对方不回，这个线程就一直挂在那里，
     * 线程池里的线程被慢慢占满，故障从"一个上游慢"扩散成"整个服务不可用"。
     */
    @Bean("pendingRestTemplate")
    public RestTemplate pendingRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(300))
                .readTimeout(Duration.ofMillis(1000))
                .build();
    }
}
