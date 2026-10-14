package com.wufeng.lab.pending;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 启动时绑定配置；修改配置后需重启，本工程没有运行时动态刷新。 */
@ConfigurationProperties(prefix = "pending")
public class PendingProperties {

    /** 要聚合的上游系统名单。真实项目里这份名单来自配置或数据库，不该写死在代码里。 */
    private List<String> systems = List.of("OA", "ERP", "HR", "FIN");

    /** 单请求实验起点；生产需结合到达率、耗时、CPU 和下游容量压测。 */
    private int corePoolSize = 4;

    /** 最大线程数。允许突发时多开几个，但必须给上限 —— 线程本身也吃内存和上下文切换。 */
    private int maxPoolSize = 8;

    /**
     * 队列容量。**必须是有界队列**。
     * 用 LinkedBlockingQueue 无参构造（容量 Integer.MAX_VALUE）后果很严重：
     * 队列永远不满 → maximumPoolSize 形同虚设 → 任务无限堆积 → 最后 OOM。
     */
    private int queueCapacity = 16;

    /** 非核心线程的空闲存活时间。 */
    private long keepAliveSeconds = 60;

    /**
     * 整页聚合的时间预算（毫秒），不是"每一个上游各等多久"。
     * 超过就降级该上游，实际响应仍有调度、序列化等开销，不是硬实时保证。
     */
    private long timeoutMillis = 250;

    /** 上游待办接口的地址前缀（默认指向本项目自带的假上游）。 */
    private String baseUrl = "http://localhost:8080/mock/pending";

    public List<String> getSystems() { return systems; }
    public void setSystems(List<String> systems) { this.systems = systems; }

    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public long getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(long keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }

    public long getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
