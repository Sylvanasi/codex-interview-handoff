package com.wufeng.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.wufeng.lab.pending.MockPendingUpstreamController;
import com.wufeng.lab.pending.PendingAggregationService;

/**
 * 待办聚合的自动化验收。
 *
 * 用 DEFINED_PORT 起真实端口，是因为聚合服务要通过 HTTP 调本项目自己的假上游，
 * 没有真实端口就会连接被拒。
 *
 * 注意 JUnit 5 的方法名直接用中文 —— Java 标识符支持，读起来比拼音清楚。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class PendingAggregationTest {

    private static final List<String> SYSTEMS = List.of("OA", "ERP", "HR", "FIN");

    @Autowired
    private PendingAggregationService aggregationService;

    @Autowired
    private MockPendingUpstreamController upstream;

    @AfterEach
    void restoreUpstreams() {
        for (String system : SYSTEMS) {
            upstream.config(system, "OK", 0);
        }
    }

    @Test
    void 健康场景_四个系统全部返回共八条待办() {
        Map<String, Object> result = aggregationService.aggregate(true);

        assertThat(result.get("degradedSystems")).as("不该有降级系统").isEqualTo(List.of());
        assertThat(result.get("itemCount")).as("4 个系统 x 2 条").isEqualTo(8);
        assertThat(totalCost(result)).as("健康场景不该超过时间预算太多").isLessThan(1000L);
    }

    @Test
    void 单个上游报错_只降级该系统其余照常返回() {
        upstream.config("ERP", "FAIL", 0);

        Map<String, Object> result = aggregationService.aggregate(true);

        assertThat(stateOf(result, "ERP")).as("ERP 应该被降级为 ERROR").isEqualTo("ERROR");
        assertThat(stateOf(result, "OA")).as("其它系统不该受影响").isEqualTo("OK");
        assertThat(stateOf(result, "FIN")).as("其它系统不该受影响").isEqualTo("OK");
        assertThat(result.get("degradedSystems")).as("只降级 ERP").isEqualTo(List.of("ERP"));
        assertThat(result.get("itemCount")).as("8 - 2").isEqualTo(6);
    }

    @Test
    void 单个上游超时_只降级该系统且整页耗时仍受预算约束() {
        upstream.config("HR", "SLOW", 2_000);

        Map<String, Object> result = aggregationService.aggregate(true);

        assertThat(stateOf(result, "HR")).as("HR 应该被判超时降级").isEqualTo("TIMEOUT");
        assertThat(result.get("degradedSystems")).as("只降级 HR").isEqualTo(List.of("HR"));
        assertThat(result.get("itemCount")).as("仍然拿到其余三个系统的 6 条").isEqualTo(6);
        assertThat(totalCost(result)).as("整页时间由预算约束，不该等到上游自己返回").isLessThan(1_500L);
    }

    @Test
    void 线程池状态可以观测() {
        Map<String, Object> snapshot = aggregationService.poolSnapshot();

        assertThat(snapshot.get("corePoolSize")).isEqualTo(4);
        assertThat(snapshot.get("maxPoolSize")).isEqualTo(8);
        assertThat(snapshot.get("queueCapacity")).isEqualTo(16);
    }

    private long totalCost(Map<String, Object> result) {
        return ((Number) result.get("totalCostMillis")).longValue();
    }

    @SuppressWarnings("unchecked")
    private String stateOf(Map<String, Object> result, String system) {
        List<Map<String, Object>> systems = (List<Map<String, Object>>) result.get("systems");
        return systems.stream()
                .filter(one -> system.equals(one.get("system")))
                .map(one -> (String) one.get("state"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("结果里没有系统 " + system));
    }
}
