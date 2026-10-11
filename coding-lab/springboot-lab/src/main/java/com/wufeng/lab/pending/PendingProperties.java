package com.wufeng.lab.pending;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 待办聚合的可调参数。
 *
 * 全部做成配置而不是写死在代码里，是因为线程池参数和超时阈值**必须能改**：
 * 上线后发现上游变慢、或者高峰期队列打满，你要能在不重新发版的前提下调参。
 * （真实生产里更进一步的做法是接配置中心，运行期动态改 corePoolSize / maximumPoolSize。）
 */
@ConfigurationProperties(prefix = "pending")
public class PendingProperties {

    /** 要聚合的上游系统名单。真实项目里这份名单来自配置或数据库，不该写死在代码里。 */
    private List<String> systems = List.of("OA", "ERP", "HR", "FIN");

    /**
     * 核心线程数。
     * 这是 IO 密集型任务（全是在等上游 HTTP 响应），经验公式：N * (1 + 等待时间 / 计算时间)。
     * 4 个上游、几乎全在等待 → 先按上游数量给 4。这个定法能被追问得住，
     * 比拍脑袋写 "2N" 更站得住。
     */
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
     * 超过就降级该上游，保证整页最慢不超过这个数。
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
