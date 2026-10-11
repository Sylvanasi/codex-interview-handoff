package com.wufeng.lab.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.wufeng.lab.config.KdxProperties;
import com.wufeng.lab.dto.KdxPushRequest;
import com.wufeng.lab.dto.KdxPushResponse;
import com.wufeng.lab.dto.TokenView;

/**
 * 假的"金蝶云星瀚"，用来让我们自己测自己。
 *
 * 为什么要在同一个工程里写一个 mock 端？
 * 1. 不依赖外网、不依赖对方联调环境，随时能复现问题；
 * 2. 可以把对方的"坏行为"打开来试：返回 401、响应超时、同一单号报重复 ——
 *    这些在真实联调里很难等，但恰恰是你最需要提前想清楚的场景；
 * 3. 面试时你可以直接说"我自己写了 mock 端模拟对方的超时和重复报错，把幂等和查单都验证过一遍"。
 *
 * 【注意】状态都放在这个单例 Bean 的字段里，重启即丢，只适合做实验。
 */
@RestController
@RequestMapping("/mock/kdx")
public class MockKdxController {

    private static final Logger log = LoggerFactory.getLogger(MockKdxController.class);

    private final KdxProperties props;

    /** 对方一共收到多少次推送请求（含重复）。 */
    private final AtomicInteger totalReceived = new AtomicInteger();

    /** 每个业务单号各被推了几次。 */
    private final Map<String, Integer> receivedByBizId = new ConcurrentHashMap<>();

    /** 对方"已入账"的单号集合 —— 判断重复就靠它。 */
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    /** 已签发的 token 及失效时刻。 */
    private final Map<String, Long> issuedTokens = new ConcurrentHashMap<>();
    private final AtomicInteger tokenIssuedCount = new AtomicInteger();

    /** 可切换的"坏行为"开关，用来复现问题。 */
    private volatile boolean dedupByBizId = false;        // 对方是否按单号做幂等（默认：不幂等，直接报错）
    private volatile boolean timeoutAfterSuccess = false; // 处理成功但响应超时
    private volatile long latencyMillis = 0;              // 人为的响应延迟

    public MockKdxController(KdxProperties props) {
        this.props = props;
    }

    // ==================== 凭证 ====================

    /**
     * 签发 token（对应你项目里"向认证中心换取凭证"那一步）。
     */
    @PostMapping("/token")
    public TokenView token(@RequestParam String appKey, @RequestParam String appSecret) {
        if (!props.getAppKey().equals(appKey) || !props.getAppSecret().equals(appSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "appKey/appSecret 不正确");
        }
        long ttl = props.getTokenTtlSeconds();
        String token = "TK-" + System.nanoTime();
        issuedTokens.put(token, System.currentTimeMillis() + ttl * 1000L);
        tokenIssuedCount.incrementAndGet();
        log.info("【假金蝶】签发 token={}，有效期 {} 秒，累计签发 {} 次", token, ttl, tokenIssuedCount.get());
        return new TokenView(token, ttl);
    }

    // ==================== 台账推送 ====================

    /**
     * 收台账。三种坏行为可以通过 /mock/kdx/config 打开：
     *   timeoutAfterSuccess=true  → 其实处理成功了，但故意卡住不回，模拟读超时；
     *   dedupByBizId=false（默认）→ 同一单号再来一次直接报 409，这是很多真实系统的做法；
     *   dedupByBizId=true         → 同一单号再来返回"已受理"，即对方替我们做了幂等。
     */
    @PostMapping("/ledger")
    public KdxPushResponse ledger(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody KdxPushRequest request) {

        requireValidToken(authorization);

        String bizId = request.bizId();
        totalReceived.incrementAndGet();
        receivedByBizId.merge(bizId, 1, Integer::sum);
        log.info("【假金蝶】收到台账推送 bizId={}，这是本单第 {} 次被推", bizId, receivedByBizId.get(bizId));

        if (latencyMillis > 0) {
            sleep(latencyMillis);
        }

        if (timeoutAfterSuccess) {
            // 关键：故意比调用方的读超时还慢，而且"其实已经处理了"
            processed.add(bizId);
            sleep(props.getReadTimeoutMillis() + 500L);
            return new KdxPushResponse(true, false, "SLOW", "处理成功但响应超时（实验用）");
        }

        if (!processed.add(bizId)) {
            if (dedupByBizId) {
                return new KdxPushResponse(true, true, "DUPLICATE", "该单据已受理（对方按单号幂等）");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该单据已存在，拒绝重复入账");
        }

        return new KdxPushResponse(true, false, "0", "受理成功");
    }

    /** 查单接口：超时之后问对方"这张单你到底收到没有"。 */
    @GetMapping("/ledger/{bizId}")
    public Map<String, Object> queryLedger(@PathVariable String bizId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bizId", bizId);
        result.put("exists", processed.contains(bizId));
        return result;
    }

    // ==================== 观测与开关 ====================

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReceived", totalReceived.get());
        stats.put("receivedByBizId", new LinkedHashMap<>(receivedByBizId));
        stats.put("processedBizIds", new ArrayList<>(processed));
        stats.put("tokenIssuedCount", tokenIssuedCount.get());
        stats.put("dedupByBizId", dedupByBizId);
        stats.put("timeoutAfterSuccess", timeoutAfterSuccess);
        stats.put("latencyMillis", latencyMillis);
        return stats;
    }

    /** 打开/关闭对方的坏行为，用来复现问题。 */
    @PostMapping("/config")
    public Map<String, Object> config(
            @RequestParam(required = false) Boolean dedupByBizId,
            @RequestParam(required = false) Boolean timeoutAfterSuccess,
            @RequestParam(required = false) Long latencyMillis) {
        if (dedupByBizId != null) {
            this.dedupByBizId = dedupByBizId;
        }
        if (timeoutAfterSuccess != null) {
            this.timeoutAfterSuccess = timeoutAfterSuccess;
        }
        if (latencyMillis != null) {
            this.latencyMillis = latencyMillis;
        }
        return stats();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        totalReceived.set(0);
        receivedByBizId.clear();
        processed.clear();
        tokenIssuedCount.set(0);
        dedupByBizId = false;
        timeoutAfterSuccess = false;
        latencyMillis = 0;
        // 注意：这里故意不清空已签发的 token。
        // 业务状态重置（清空收件记录）和凭证失效是两件事，混在一起会让排查变难。
        // 想让凭证失效，调 /revoke-tokens。
        log.info("【假金蝶】业务状态已重置（已签发的凭证保留）");
        return stats();
    }

    /**
     * 让所有已签发的 token 立即失效，用来模拟"对方提前吊销凭证"。
     * 此时调用方手里那个缓存 token 变成"本地认为有效、对方认为无效"，
     * 下一次业务调用会拿到 401 —— 这正是要验证的自愈路径：清缓存 → 换一次 → 重试一次。
     */
    @PostMapping("/revoke-tokens")
    public Map<String, Object> revokeTokens() {
        int revoked = issuedTokens.size();
        issuedTokens.clear();
        log.warn("【假金蝶】已吊销 {} 个凭证，调用方下次调用会拿到 401", revoked);
        return stats();
    }

    // ==================== 内部工具 ====================

    private void requireValidToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少 Authorization 头");
        }
        String token = authorization.substring("Bearer ".length());
        Long expireAt = issuedTokens.get(token);
        if (expireAt == null || expireAt < System.currentTimeMillis()) {
            // 这一步模拟"对方不认你这个凭证"，用来触发调用方的清缓存 + 重试
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "凭证无效或已过期");
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
