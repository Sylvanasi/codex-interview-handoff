package com.wufeng.lab;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 幂等的自动化验收。
 *
 * 【为什么用 DEFINED_PORT 而不是 RANDOM_PORT】
 * 本项目里"调用方"和"被调用的假金蝶"在同一个进程里，客户端是拿着配置里的
 * kdx.base-url（http://localhost:8080/mock/kdx）去发的请求。
 * 用 RANDOM_PORT 的话端口随机，这个配置就对不上了。
 * 真实项目里这一类地址本来就该从配置注入，所以这里固定端口最贴近真实用法。
 * （代价：本机 8080 被别的程序占用时测试会失败，先关掉占用者即可。）
 *
 * 【测试要证明的四件事】
 * 1. 串行重复调用 → 只有第一次真的打到对方；
 * 2. 20 个线程并发推同一张单 → 对方只收到 1 次；
 * 3. 本侧台账丢了（模拟缓存被清/重启）→ 对方按单号幂等兜底，我们标记为已受理；
 * 4. 对方响应超时但实际处理成功 → 本侧查单确认，不重复推送。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class IdempotentPushTest {

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void resetBothSides() {
        // 每个用例开始前，把本侧台账和假金蝶的状态都清干净
        rest.postForEntity("/mock/kdx/reset", null, String.class);
        rest.postForEntity("/api/reset", null, String.class);
    }

    @Test
    @DisplayName("串行推两次：第二次在本地就被拦掉，不打对方接口")
    void serialPushIsDedupedLocally() {
        rest.postForEntity("/api/contracts/HT-001/push", null, Map.class);
        rest.postForEntity("/api/contracts/HT-001/push", null, Map.class);

        Map<?, ?> stats = rest.getForObject("/api/stats", Map.class);
        assertThat(number(stats, "upstreamCalls")).isEqualTo(1);
        assertThat(number(stats, "skippedByLocalLedger")).isEqualTo(1);

        Map<?, ?> kdxStats = rest.getForObject("/mock/kdx/stats", Map.class);
        assertThat(number(kdxStats, "totalReceived")).isEqualTo(1);
    }

    @Test
    @DisplayName("20 个线程并发推同一张单：对方只收到 1 次")
    void concurrentPushHitsUpstreamOnce() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    rest.postForEntity("/api/contracts/HT-002/push", null, Map.class);
                } catch (Exception ignored) {
                    // 并发演示：个别请求失败不影响断言
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        Map<?, ?> kdxStats = rest.getForObject("/mock/kdx/stats", Map.class);
        assertThat(number(kdxStats, "totalReceived")).isEqualTo(1);
    }

    @Test
    @DisplayName("本侧台账丢了：对方按单号幂等兜底，仍能判定为已受理")
    void upstreamDedupSavesUsWhenLocalLedgerIsLost() {
        rest.postForEntity("/mock/kdx/config?dedupByBizId=true", null, Map.class);
        rest.postForEntity("/api/contracts/HT-003/push", null, Map.class);

        // 模拟"本地台账丢了"（进程重启、Redis 被清空、TTL 过期）
        rest.postForEntity("/api/reset", null, Map.class);

        Map<?, ?> second = rest.postForEntity("/api/contracts/HT-003/push", null, Map.class).getBody();
        assertThat(second).isNotNull();
        assertThat(second.get("state")).isEqualTo("DUPLICATE_ACCEPTED");
    }

    @Test
    @DisplayName("对方处理成功但响应超时：本侧查单确认，不重复推送")
    void timeoutIsResolvedByQueryingUpstream() {
        rest.postForEntity("/mock/kdx/config?timeoutAfterSuccess=true", null, Map.class);

        Map<?, ?> outcome = rest.postForEntity("/api/contracts/HT-004/push", null, Map.class).getBody();

        assertThat(outcome).isNotNull();
        assertThat(outcome.get("state")).isEqualTo("TIMEOUT_CONFIRMED");
        Map<?, ?> kdxStats = rest.getForObject("/mock/kdx/stats", Map.class);
        assertThat(number(kdxStats, "totalReceived")).isEqualTo(1);
    }

    @Test
    @DisplayName("对方提前吊销凭证：401 自愈 —— 清缓存、换一次、只重试一次")
    void handlesRevokedTokenByRefreshingOnce() {
        // 先用 HT-005 把 token 换出来并缓存住
        rest.postForEntity("/api/contracts/HT-005/push", null, Map.class);
        // 对方把已签发的凭证全部吊销（模拟对方重启 / 主动吊销）
        rest.postForEntity("/mock/kdx/revoke-tokens", null, Map.class);

        // 换一张新单推：本地台账拦不住它，会真的带着旧 token 打过去 → 401 → 自愈后成功
        Map<?, ?> outcome = rest.postForEntity("/api/contracts/HT-006/push", null, Map.class).getBody();

        assertThat(outcome).isNotNull();
        assertThat(outcome.get("state")).isEqualTo("SUCCESS");
    }

    private int number(Map<?, ?> map, String key) {
        Object value = map.get(key);
        assertThat(value).as("统计字段 " + key + " 应该存在").isNotNull();
        return ((Number) value).intValue();
    }
}
