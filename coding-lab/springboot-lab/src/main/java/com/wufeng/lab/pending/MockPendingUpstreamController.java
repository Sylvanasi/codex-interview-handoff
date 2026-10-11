package com.wufeng.lab.pending;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 假上游：模拟 4 个业务系统（OA / ERP / HR / FIN）的"待办查询接口"。
 *
 * 它存在的唯一目的，是让你能**亲手制造故障**，看聚合侧怎么应对：
 *   mode=FAIL  → 返回 500，模拟某个系统挂了
 *   mode=SLOW  → 睡 delayMillis 毫秒，模拟某系统变慢
 *   mode=STUCK → 睡 5 秒不返回，模拟"卡死"（远超客户端读超时）
 */
@RestController
@RequestMapping("/mock/pending")
public class MockPendingUpstreamController {

    /** 每个系统的行为开关。真实项目里没有这个接口，这纯粹是为了做实验。 */
    private final Map<String, Setting> settings = new ConcurrentHashMap<>();

    public MockPendingUpstreamController() {
        for (String system : List.of("OA", "ERP", "HR", "FIN")) {
            settings.put(system, new Setting("OK", 0));
        }
    }

    /** 设置某个上游的行为：mode = OK / SLOW / FAIL / STUCK。 */
    @PostMapping("/config")
    public Map<String, Object> config(@RequestParam String system,
                                      @RequestParam(defaultValue = "OK") String mode,
                                      @RequestParam(defaultValue = "0") long delayMillis) {
        settings.put(system, new Setting(mode, delayMillis));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("system", system);
        out.put("mode", mode);
        out.put("delayMillis", delayMillis);
        return out;
    }

    @GetMapping("/state")
    public Map<String, Setting> state() {
        return new LinkedHashMap<>(settings);
    }

    @GetMapping("/{system}")
    public ResponseEntity<List<PendingItem>> pending(@PathVariable String system) throws InterruptedException {
        Setting setting = settings.getOrDefault(system, new Setting("OK", 0));
        switch (setting.mode()) {
            case "FAIL" -> {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            case "STUCK" -> Thread.sleep(5_000);
            case "SLOW" -> Thread.sleep(setting.delayMillis());
            default -> { }
        }
        return ResponseEntity.ok(items(system));
    }

    /** 每个系统固定返回 2 条，方便数数。 */
    private List<PendingItem> items(String system) {
        List<PendingItem> list = new ArrayList<>();
        list.add(new PendingItem(system, system + "-001", system + " 待办一", "/" + system + "/task/001"));
        list.add(new PendingItem(system, system + "-002", system + " 待办二", "/" + system + "/task/002"));
        return list;
    }

    public record Setting(String mode, long delayMillis) {
    }
}
