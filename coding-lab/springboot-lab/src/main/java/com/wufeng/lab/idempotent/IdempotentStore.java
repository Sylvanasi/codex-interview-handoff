package com.wufeng.lab.idempotent;

/**
 * 幂等拦截层的抽象：可能有三种实现 —— 进程内 Map、真 Redis、以后换成别的缓存。
 *
 * 面试点：**面向接口编程解决的是"替换成本"**。
 * 这份接口只声明四件事，注意每一件事的语义：
 *
 *   claim       原子占位。必须是"判存在 + 写标记"一次完成，
 *               绝对不能拆成 exists + set 两步 —— 两步之间那条缝就是重复推送的来源。
 *   markDone    推送成功后记完成状态。
 *   release     推送失败后释放占位，让后面还能重试；必须带 owner，防止误删别人的。
 *   isDone      查询是否已完成，供对账和排查用。
 */
public interface IdempotentStore {

    /**
     * 占位。成功返回**本次调用的归属标识（owner token）**，已被占用返回 null。
     *
     * 为什么返回值不是 boolean，而是一个 owner？
     * 因为"占位"这件事必须能回答"这个位是谁占的"。只用 true/false 的话，
     * 后面释放的时候你无法证明"这个位是我的"，就可能删掉别人刚占的位。
     * 面试点：**锁/占位的 value 里必须带归属信息，不能只放一个状态值。**
     */
    String claim(String bizId, long ttlSeconds);

    /** 标记为已完成（保留 TTL，过期后自然清理）。 */
    void markDone(String bizId, long ttlSeconds);

    /**
     * 释放自己占的位。**必须带上自己 claim 时拿到的 owner**：
     * owner 对不上就什么都不做（说明这个位已经被别人接手，或者已经完成了）。
     */
    void release(String bizId, String owner);

    /**
     * 强制释放：给对账这类系统级清理用，不校验归属。
     * 之所以单独一个方法，是不想让"业务释放"顺手用上它 ——
     * 权限越小越好用错。
     */
    void forceRelease(String bizId);

    boolean isDone(String bizId);

    /** 当前用的是哪个实现，打在日志和 /api/infra 里，避免"我以为是 Redis，其实是内存"。 */
    String backend();

    /** 演示用：清空。生产不会提供这种接口。 */
    void clear();
}
