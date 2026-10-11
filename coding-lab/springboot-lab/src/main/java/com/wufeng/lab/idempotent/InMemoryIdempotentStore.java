package com.wufeng.lab.idempotent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内实现：用 ConcurrentHashMap 的 CAS 模拟 Redis 的 SETNX 语义。
 * 存在的意义是"零依赖也能跑"，以及给你一个对照组去感受 Redis 版的行为差异。
 *
 * 它的致命边界（面试问到"为什么不能用本地缓存做幂等"就答这个）：
 *   多实例部署时每个节点各有一份，A 节点的标记 B 节点看不见；
 *   进程重启就全丢；没法跨服务共享。所以它只能当单机实验用。
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    static final int IN_PROGRESS = 1;
    static final int DONE = 2;

    /** owner 是"谁占的位"；DONE_OWNER 用来表示这一条已经完成，不代表任何请求。 */
    private record Entry(String owner, int state, long expireAt) {
    }

    private static final String DONE_OWNER = "__DONE__";

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public String claim(String bizId, long ttlSeconds) {
        String owner = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long ttlMillis = ttlSeconds * 1000L;
        while (true) {
            Entry old = map.get(bizId);
            if (old != null && old.expireAt() > now) {
                return null;                        // 有人正在推，或已经推过
            }
            if (old == null) {
                if (map.putIfAbsent(bizId, new Entry(owner, IN_PROGRESS, now + ttlMillis)) == null) {
                    return owner;
                }
            } else if (map.replace(bizId, old, new Entry(owner, IN_PROGRESS, now + ttlMillis))) {
                return owner;                       // 旧标记已过期，CAS 替换成功
            }
            // 被别人抢先了，重试一次循环
        }
    }

    @Override
    public void markDone(String bizId, long ttlSeconds) {
        map.put(bizId, new Entry(DONE_OWNER, DONE, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    @Override
    public void release(String bizId, String owner) {
        map.computeIfPresent(bizId, (k, v) ->
                (v.state() == IN_PROGRESS && v.owner().equals(owner)) ? null : v);
    }

    @Override
    public void forceRelease(String bizId) {
        map.computeIfPresent(bizId, (k, v) -> v.state() == IN_PROGRESS ? null : v);
    }

    @Override
    public boolean isDone(String bizId) {
        Entry entry = map.get(bizId);
        return entry != null && entry.state() == DONE && entry.expireAt() > System.currentTimeMillis();
    }

    @Override
    public String backend() {
        return "memory(ConcurrentHashMap)";
    }

    @Override
    public void clear() {
        map.clear();
    }
}
