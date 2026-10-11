package com.wufeng.lab.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wufeng.lab.client.KdxBusinessException;
import com.wufeng.lab.client.KdxClient;
import com.wufeng.lab.client.KdxTimeoutException;
import com.wufeng.lab.config.LabProperties;
import com.wufeng.lab.dto.KdxPushResponse;
import com.wufeng.lab.dto.PushOutcome;
import com.wufeng.lab.idempotent.IdempotentStore;
import com.wufeng.lab.repository.PushRecordRepository;

/**
 * 合同推送服务：两层幂等 + 三种回执分支，是整个实验台的主角。
 *
 * 完整流程：
 * ① store.claim(bizId)                第一道闸：缓存（Redis SETNX），挡住并发和重复提交
 *        抢不到 → SKIPPED_BY_CACHE，根本不打对方接口
 * ② repository.insertIfAbsent(bizId)  第二道闸：数据库唯一索引，缓存失效时的最终兜底
 *        插不进去 → BLOCKED_BY_DB，说明这张单已经在台账里
 * ③ 调对方接口
 *        成功            → 两道闸都记 DONE
 *        对方报重复受理   → 也记 DONE，但要告警：这说明本地漏了一环
 *        明确失败        → 释放缓存占位 + 台账记 FAILED（4xx 不重试，5xx 可退避重试）
 *        超时（未知状态） → 先查单：对方有就记 DONE；查不到记 FAILED 等对账
 *
 * 为什么两道闸都要？
 * 只有缓存：TTL 过期、Redis 重启、多节点丢失 → 重复推送。
 * 只有数据库：每个请求都要打一次库，高频场景下数据库压力大、也慢。
 * 缓存挡流量，数据库保正确性 —— 几乎所有"幂等 / 防重"方案都是这个结构。
 */
@Service
public class ContractPushService {

    private static final Logger log = LoggerFactory.getLogger(ContractPushService.class);

    private final IdempotentStore store;
    private final PushRecordRepository repository;
    private final KdxClient kdxClient;
    private final LabProperties props;

    /** 真正打到对方接口的次数（不含被两道闸拦下的），用来证明幂等真的生效。 */
    private final AtomicInteger upstreamCalls = new AtomicInteger();

    /** 被缓存这道闸拦下的次数。 */
    private final AtomicInteger skippedByCache = new AtomicInteger();

    /** 被数据库这道闸拦下的次数 —— 这个数字大于 0，就说明"缓存曾经失效过"。 */
    private final AtomicInteger blockedByDatabase = new AtomicInteger();

    public ContractPushService(IdempotentStore store,
                               PushRecordRepository repository,
                               KdxClient kdxClient,
                               LabProperties props) {
        this.store = store;
        this.repository = repository;
        this.kdxClient = kdxClient;
        this.props = props;
    }

    public PushOutcome push(String bizId, String payload) {
        long ttl = props.getDedupTtlSeconds();

        // 第一道闸：缓存占位（对应 Redis 的 SET key value NX EX ttl）。
        // claim 返回的 owner 一定要留着：后面释放时靠它证明"这个位是我占的"。
        String owner = store.claim(bizId, ttl);
        if (owner == null) {
            skippedByCache.incrementAndGet();
            log.info("第一道闸拦下 bizId={}：缓存里已有标记，没有打对方接口", bizId);
            return new PushOutcome(bizId, "SKIPPED_BY_CACHE", "缓存里已有占位标记，直接跳过");
        }

        // 第二道闸：数据库唯一索引（INSERT 撞约束 = 已经有人处理过这张单）
        if (!repository.insertIfAbsent(bizId)) {
            blockedByDatabase.incrementAndGet();
            store.markDone(bizId, ttl);
            log.warn("第二道闸拦下 bizId={}：数据库台账已有记录，说明缓存曾经失效过", bizId);
            return new PushOutcome(bizId, "BLOCKED_BY_DB", "数据库台账已有这张单，判定为重复");
        }

        try {
            upstreamCalls.incrementAndGet();
            KdxPushResponse response = kdxClient.pushLedger(bizId, payload);

            // 先判 duplicate 再判 success：对方幂等受理时两个字段都是 true，
            // 顺序写反就永远看不到"对方替我们兜了底"这个信号。
            if (response.duplicate()) {
                log.warn("对方按幂等受理了重复推送 bizId={} —— 本地两道闸都没拦住，要排查", bizId);
                store.markDone(bizId, ttl);
                repository.markDone(bizId);
                return new PushOutcome(bizId, "DUPLICATE_ACCEPTED", response.message());
            }
            if (response.success()) {
                store.markDone(bizId, ttl);
                repository.markDone(bizId);
                return new PushOutcome(bizId, "SUCCESS", response.message());
            }

            // 对方明确说没成：释放缓存占位，台账记 FAILED
            store.release(bizId, owner);
            repository.markFailed(bizId, "对方明确失败：" + response.message());
            return new PushOutcome(bizId, "FAILED", response.message());

        } catch (KdxTimeoutException e) {
            // 超时 = 未知状态。不直接重试，先问对方"你收到没有"
            log.warn("推送超时 bizId={}，改为查询对方状态", bizId);
            try {
                if (kdxClient.ledgerExists(bizId)) {
                    store.markDone(bizId, ttl);
                    repository.markDone(bizId);
                    return new PushOutcome(bizId, "TIMEOUT_CONFIRMED", "超时后查单：对方已受理，本地补记为 DONE");
                }
            } catch (RuntimeException queryError) {
                log.error("查单也失败了 bizId={}，留给对账任务处理", bizId, queryError);
            }
            store.release(bizId, owner);
            repository.markFailed(bizId, "超时且查单未确认，等对账");
            return new PushOutcome(bizId, "TIMEOUT_UNKNOWN", "超时且查不到结果，等对账任务收尾");

        } catch (KdxBusinessException e) {
            // 真实项目里这里还要分 4xx（不重试，人工介入）和 5xx（可退避重试）
            store.release(bizId, owner);
            repository.markFailed(bizId, e.getMessage());
            return new PushOutcome(bizId, "FAILED", e.getMessage());
        }
    }

    /** 并发推送同一张单 —— 用来直观看到两道闸的效果。真实业务不会这么写。 */
    public Map<String, Object> pushConcurrently(String bizId, String payload, int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<PushOutcome> outcomes = new ArrayList<>();
        Map<String, Integer> stateCounter = new LinkedHashMap<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);   // 让所有线程尽量同时冲进去，放大并发窗口
                    PushOutcome outcome = push(bizId, payload);
                    synchronized (outcomes) {
                        outcomes.add(outcome);
                        stateCounter.merge(outcome.state(), 1, Integer::sum);
                    }
                } catch (Exception e) {
                    log.warn("并发推送线程异常：{}", e.toString());
                } finally {
                    done.countDown();
                }
            });
        }

        try {
            done.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bizId", bizId);
        result.put("threads", threads);
        result.put("outcomes", outcomes);
        result.put("stateCount", stateCounter);
        result.put("upstreamCalls", upstreamCalls.get());
        result.put("skippedByLocalLedger", skippedByCache.get());
        return result;
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("idempotentStore", store.backend());
        stats.put("pushRecordRepository", repository.backend());
        stats.put("upstreamCalls", upstreamCalls.get());
        stats.put("skippedByLocalLedger", skippedByCache.get());
        stats.put("blockedByDatabase", blockedByDatabase.get());
        stats.put("pushRecordCount", repository.count());
        return stats;
    }

    /** 台账明细，给演示接口看。 */
    public List<Map<String, Object>> records() {
        return repository.listAll();
    }

    /** 演示用：把两层台账和计数器都清空。 */
    public void reset() {
        store.clear();
        repository.clear();
        upstreamCalls.set(0);
        skippedByCache.set(0);
        blockedByDatabase.set(0);
        log.info("幂等台账（缓存 + 数据库）与计数器已重置");
    }

    /**
     * 演示用：**只清缓存，保留数据库台账**。
     *
     * 这是整个实验里最有价值的一次演示：它模拟"Redis 重启 / 被清空 / TTL 过期"，
     * 然后你再推同一张单，会发现第二道闸把它拦住了，返回 BLOCKED_BY_DB。
     * 面试官问"你的幂等只靠 Redis 吗？Redis 挂了怎么办"——现场跑这一遍给他看。
     */
    public Map<String, Object> resetCacheOnly() {
        store.clear();
        upstreamCalls.set(0);
        skippedByCache.set(0);
        blockedByDatabase.set(0);
        log.warn("只清了缓存，数据库台账保留 —— 下一次推送应该由数据库这道闸拦下");
        return stats();
    }
}
