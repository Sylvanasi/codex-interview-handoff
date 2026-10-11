package com.wufeng.lab.idempotent;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 实现 —— 这才是简历上"基于 Redis 实现幂等"对应的真实代码。
 *
 * 【三个必须讲清的细节】
 *
 * 1. 占位只用一条命令：SET key value NX EX ttl。
 *    Spring Data Redis 里是 setIfAbsent(key, value, Duration)，底层就是 SETNX + EXPIRE 的原子组合。
 *    面试官如果问"为什么不先 exists 再 set"：因为两条命令之间有并发窗口，
 *    20 个请求会一起查到"不存在"，然后一起写、一起推。idempotent-push 实验里演示过。
 *
 * 2. 释放占位要用 Lua。
 *    朴素写法是"先 GET 看是不是自己占的 IN_PROGRESS，再 DEL"，这又是两步非原子操作 ——
 *    你 GET 完之后、DEL 之前，标记可能已经过期并被另一个请求重新占上了，你 DEL 掉的其实是别人的占位。
 *    Lua 脚本在 Redis 里是单线程原子执行的，把"判断 + 删除"合成一步，这也是 Lua 在 Redis 里最典型的用途。
 *
 * 3. TTL 必须有。
 *    没有过期时间，一旦推送失败又没走到 release（进程被 kill），这张单就永远被自己锁死 —— 业务上的"永久卡单"。
 *    TTL 的取舍：太短会在业务重试窗口内失效导致重复推送，太长会推迟"漏推后的自动恢复"。
 */
public class RedisIdempotentStore implements IdempotentStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotentStore.class);

    private static final String DONE = "DONE";

    /**
     * 判断 + 删除，一条 Lua 脚本搞定。
     * ARGV[1] 是本次调用自己的 owner：只有"这个位确实是我占的"才删。
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    /** 强制释放：只要不是已完成，就删掉。对账任务收尾用。 */
    private static final DefaultRedisScript<Long> FORCE_RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisIdempotentStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String claim(String bizId, long ttlSeconds) {
        // value 放 owner，而不是放"IN_PROGRESS"这个状态字符串 —— 这是关键。
        // 状态值谁写都一样，出了事分不清是谁的位；owner 让每个请求的占位都是唯一可辨认的。
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(key(bizId), owner, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired) ? owner : null;
    }

    @Override
    public void markDone(String bizId, long ttlSeconds) {
        redis.opsForValue().set(key(bizId), DONE, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void release(String bizId, String owner) {
        Long deleted = redis.execute(RELEASE_SCRIPT, List.of(key(bizId)), owner);
        log.debug("释放占位 bizId={}，Lua 返回 {}（1=删掉了，0=不是自己占的或已完成）", bizId, deleted);
    }

    @Override
    public void forceRelease(String bizId) {
        redis.execute(FORCE_RELEASE_SCRIPT, List.of(key(bizId)), DONE);
    }

    @Override
    public boolean isDone(String bizId) {
        return DONE.equals(redis.opsForValue().get(key(bizId)));
    }

    @Override
    public String backend() {
        return "redis(prefix=" + keyPrefix + ")";
    }

    @Override
    public void clear() {
        // 演示用。生产里禁止 KEYS：它复杂度 O(N)，会阻塞 Redis 单线程。
        // 正确做法是用 SCAN 游标分批扫，或者用带前缀的 key 设计 + 逻辑库隔离。
        Set<String> keys = redis.keys(keyPrefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String key(String bizId) {
        return keyPrefix + bizId;
    }
}
