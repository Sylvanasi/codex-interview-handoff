package com.wufeng.lab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本实验台自己的开关。
 *
 * 为什么要做成可切换的？因为"幂等"这件事在真实项目里是两层的：
 *   Redis  —— 快速拦截层，挡住绝大多数重复，但会过期、会重启、会丢
 *   MySQL  —— 最终兜底层，靠唯一索引保证同一张单只被处理一次
 * 两层各自可以打开/关闭，你才能亲手验证"只靠 Redis 会怎样""Redis 挂了谁兜底"。
 * 这也顺便让你在面试里能讲清一句话：**幂等不能只靠缓存。**
 */
@ConfigurationProperties(prefix = "lab")
public class LabProperties {

    /** 幂等拦截层：memory（进程内，默认，零依赖）或 redis（真实 Redis） */
    private String store = "memory";

    /** 幂等台账落库：memory 或 mysql */
    private String repository = "memory";

    /** Redis key 前缀。真实项目一定要带业务前缀，否则多个系统共用一个 Redis 时会互相踩。 */
    private String redisKeyPrefix = "oa:push:";

    /** 幂等标记的过期时间（秒）。太短会重复推，太长会占内存，要按业务节奏定。 */
    private long dedupTtlSeconds = 3600;

    /** 对账阈值：进行中且超过这么多分钟没更新的记录，交给对账任务去查单。 */
    private long stuckMinutes = 5;

    /** 对账任务执行间隔（毫秒）。 */
    private long reconcileIntervalMillis = 60000;

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }

    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }

    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }

    public long getDedupTtlSeconds() { return dedupTtlSeconds; }
    public void setDedupTtlSeconds(long dedupTtlSeconds) { this.dedupTtlSeconds = dedupTtlSeconds; }

    public long getStuckMinutes() { return stuckMinutes; }
    public void setStuckMinutes(long stuckMinutes) { this.stuckMinutes = stuckMinutes; }

    public long getReconcileIntervalMillis() { return reconcileIntervalMillis; }
    public void setReconcileIntervalMillis(long reconcileIntervalMillis) {
        this.reconcileIntervalMillis = reconcileIntervalMillis;
    }
}
