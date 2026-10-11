package com.wufeng.lab.pending;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办聚合的演示入口。
 *
 * 同样遵守"Controller 只接参数、调服务、返回"的原则，
 * 并发编排、超时、降级全部在 PendingAggregationService 里。
 */
@RestController
@RequestMapping("/api/pending")
public class PendingController {

    private final PendingAggregationService aggregationService;

    public PendingController(PendingAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * 聚合待办：parallel=true 并发（默认），false 串行。
     * 同一组上游，先用 ?parallel=false 跑一次、再用默认跑一次，对比 totalCostMillis。
     */
    @GetMapping("/aggregate")
    public Map<String, Object> aggregate(@RequestParam(defaultValue = "true") boolean parallel) {
        return aggregationService.aggregate(parallel);
    }

    /** 线程池实时状态：看 activeCount / queueSize / rejectedCount。 */
    @GetMapping("/pool")
    public Map<String, Object> pool() {
        return aggregationService.poolSnapshot();
    }
}
