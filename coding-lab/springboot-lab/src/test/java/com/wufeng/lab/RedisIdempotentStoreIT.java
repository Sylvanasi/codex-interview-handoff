package com.wufeng.lab;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.wufeng.lab.idempotent.IdempotentStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对真 Redis 的集成测试（IT = Integration Test）。
 *
 * 和单元测试的区别：单元测试用内存实现，验证"逻辑对不对"；
 * 这个测试连真 Redis，验证"我以为的 Redis 行为，和 Redis 实际行为一致吗"。
 *
 * 最后两个用例是重点：它们把"为什么释放占位必须用 Lua，而且 value 里必须带 owner"
 * 这件事演成了两步可复现的实验。
 *
 * 本机没有 Redis 时，@BeforeEach 里的 Assumptions 会让整个类跳过（skipped，不是失败），
 * 所以别人 clone 代码跑 mvn test 不会因为没装 Redis 而挂。
 */
@SpringBootTest(properties = {
        "lab.store=redis",
        "lab.repository=memory",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.timeout=2s",
        "lab.dedup-ttl-seconds=60"
})
class RedisIdempotentStoreIT {

    @Autowired
    private IdempotentStore store;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void requireRedisAndClean() {
        boolean alive;
        try {
            alive = "PONG".equalsIgnoreCase(redis.execute((RedisCallback<String>) connection -> connection.ping()));
        } catch (RuntimeException e) {
            alive = false;
        }
        Assumptions.assumeTrue(alive, "本机 6379 上没有可用的 Redis，跳过这个集成测试");
        store.clear();
    }

    @Test
    @DisplayName("SETNX 语义：第一次占位成功、第二次失败；释放自己的位之后可以重新占")
    void setnxSemantics() {
        String owner = store.claim("HT-IT-001", 60);
        assertThat(owner).isNotNull();
        assertThat(store.claim("HT-IT-001", 60)).isNull();      // 已被占住

        store.release("HT-IT-001", owner);
        assertThat(store.claim("HT-IT-001", 60)).isNotNull();   // 释放后可以重试
    }

    @Test
    @DisplayName("标记完成之后占位继续失败，isDone 为真")
    void markDoneKeepsBlocking() {
        store.claim("HT-IT-002", 60);
        store.markDone("HT-IT-002", 60);

        assertThat(store.isDone("HT-IT-002")).isTrue();
        assertThat(store.claim("HT-IT-002", 60)).isNull();
    }

    @Test
    @DisplayName("已完成之后调 release：Lua 判断状态不符，不会删掉完成标记")
    void releaseDoesNotRemoveDoneMarker() {
        String owner = store.claim("HT-IT-003", 60);
        store.markDone("HT-IT-003", 60);

        store.release("HT-IT-003", owner);      // 想释放，但值已经是 DONE，不是 owner

        assertThat(store.isDone("HT-IT-003")).isTrue();
    }

    @Test
    @DisplayName("50 个线程抢同一个键：只有一个拿到 owner")
    void concurrentAcquireOnlyOneWins() throws InterruptedException {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();
        Set<String> owners = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    String owner = store.claim("HT-IT-004", 60);
                    if (owner != null) {
                        winners.incrementAndGet();
                        owners.add(owner);
                    }
                } catch (Exception ignored) {
                    // 并发演示，忽略个别线程异常
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(winners.get()).isEqualTo(1);
        assertThat(owners).hasSize(1);
    }

    // ========================================================================
    // 下面两个用例回答同一个问题：
    // "release 为什么必须用 Lua，而且 value 里必须带 owner？"
    // ========================================================================

    @Test
    @DisplayName("场景一 · 值里带 owner：别人的占位不会被误删")
    void releaseOnlyRemovesOwnClaim() {
        String key = "oa:push:HT-IT-005";
        String ownerA = store.claim("HT-IT-005", 60);
        assertThat(ownerA).isNotNull();

        // 模拟 A 的占位已经过期（或者 Redis 抖动丢了这个 key），C 接手占位
        redis.delete(key);
        String ownerC = store.claim("HT-IT-005", 60);
        assertThat(ownerC).isNotNull();

        // A 醒过来释放"自己的"占位：Lua 里 GET 到的是 ownerC ≠ ownerA → 什么都不做
        store.release("HT-IT-005", ownerA);

        assertThat(redis.opsForValue().get(key)).isEqualTo(ownerC);
    }

    @Test
    @DisplayName("场景二 · 先 GET 再 DEL：即使值里有 owner，仍会删掉别人的占位")
    void getThenDelStillLosesEvenWithOwnerToken() throws Exception {
        String key = "oa:push:HT-IT-006";
        String ownerA = store.claim("HT-IT-006", 60);
        assertThat(ownerA).isNotNull();

        CountDownLatch aHasDoneGet = new CountDownLatch(1);
        CountDownLatch cHasClaimed = new CountDownLatch(1);
        AtomicReference<String> ownerC = new AtomicReference<>();

        // 线程 A：朴素的两步释放 —— 先 GET 判断，再 DEL
        Thread threadA = new Thread(() -> {
            String current = redis.opsForValue().get(key);   // ① GET：此刻看到的是自己的 owner
            aHasDoneGet.countDown();
            try {
                cHasClaimed.await(5, TimeUnit.SECONDS);      // ② 暂停：真实世界里这里是 GC、网络、线程调度
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ownerA.equals(current)) {
                redis.delete(key);                           // ③ DEL：**无条件删除**，不看当前值是谁的
            }
        });
        threadA.start();
        assertThat(aHasDoneGet.await(5, TimeUnit.SECONDS)).isTrue();

        // 在 A 的 GET 和 DEL 之间：A 的占位过期，C 抢到了这个位
        redis.delete(key);
        ownerC.set(store.claim("HT-IT-006", 60));
        cHasClaimed.countDown();
        threadA.join(5000);

        assertThat(ownerC.get()).isNotNull();
        // 结局：C 的占位被 A 的 DEL 删掉了 —— A 判断过 owner 也没用，因为 DEL 不校验
        assertThat(redis.opsForValue().get(key)).isNull();

        // 后果：D 请求进来会占位成功，于是 C 和 D 同时推同一张单
        assertThat(store.claim("HT-IT-006", 60)).isNotNull();
    }
}
