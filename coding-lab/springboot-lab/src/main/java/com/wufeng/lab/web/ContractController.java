package com.wufeng.lab.web;

import java.util.Map;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wufeng.lab.dto.PushOutcome;
import com.wufeng.lab.service.ContractPushService;

/**
 * 对外暴露的演示接口（我们自己的业务入口）。
 *
 * 面试点：Controller 里不要写业务逻辑。
 * 这里每个方法只有一行调用 —— Controller 的职责是"接参数、调服务、返回结果"，
 * 一旦把 if/else 和事务写进 Controller，单元测试、复用、事务边界全都会失控。
 */
@RestController
@RequestMapping("/api")
public class ContractController {

    private final ContractPushService pushService;

    public ContractController(ContractPushService pushService) {
        this.pushService = pushService;
    }

    /** 推一张单：正常情况下重复调用第二次会被本地幂等拦掉。 */
    @PostMapping("/contracts/{bizId}/push")
    public PushOutcome push(@PathVariable String bizId,
                            @RequestParam(defaultValue = "demo-payload") String payload) {
        return pushService.push(bizId, payload);
    }

    /**
     * 并发推同一张单：20 个线程同时冲进来，看对方到底收到几次。
     * 这是幂等效果最直观的验证方式。
     */
    @PostMapping("/contracts/{bizId}/push-parallel")
    public Map<String, Object> pushParallel(@PathVariable String bizId,
                                            @RequestParam(defaultValue = "20") int threads,
                                            @RequestParam(defaultValue = "demo-payload") String payload) {
        return pushService.pushConcurrently(bizId, payload, threads);
    }

    /**
     * 看推送台账的明细（状态 + 失败原因 + 更新时间）。
     * 用 MySQL 时这里读的就是 push_record 表，用内存实现时读的是进程内的表。
     */
    @GetMapping("/push-records")
    public List<Map<String, Object>> pushRecords() {
        return pushService.records();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return pushService.stats();
    }

    /** 把本侧台账和计数器清空，方便重新做实验。 */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        pushService.reset();
        return pushService.stats();
    }

    /**
     * 只清缓存、保留数据库台账 —— 用来演示"Redis 丢了，数据库还能兜住"。
     * 调用后立刻再推一次同一张单，你会看到 BLOCKED_BY_DB。
     */
    @PostMapping("/reset-cache-only")
    public Map<String, Object> resetCacheOnly() {
        return pushService.resetCacheOnly();
    }
}
