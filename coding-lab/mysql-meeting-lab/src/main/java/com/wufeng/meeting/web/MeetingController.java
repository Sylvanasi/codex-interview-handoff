package com.wufeng.meeting.web;

import com.wufeng.meeting.model.Models.*;
import com.wufeng.meeting.repository.MeetingRepository;
import com.wufeng.meeting.service.*;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class MeetingController {
    private final MeetingService meetings;
    private final MeetingRepository repository;
    private final LearningSteps steps;
    private final DatabaseExperimentService experiments;
    private final Environment environment;

    public MeetingController(MeetingService meetings, MeetingRepository repository, LearningSteps steps, DatabaseExperimentService experiments, Environment environment) {
        this.meetings = meetings;
        this.repository = repository;
        this.steps = steps;
        this.experiments = experiments;
        this.environment = environment;
    }

    @GetMapping("/lab")
    public Map<String, Object> info() {
        return Map.of("application", "会议通知Spring Boot练习", "implementation", steps.getClass().getSimpleName(), "profiles", environment.getActiveProfiles());
    }

    @PostMapping("/lab/init")
    public Map<String, Object> init() {
        return repository.init();
    }

    @PostMapping("/lab/index")
    public Map<String, Object> index() {
        steps.addTopicIndex();
        return repository.explain();
    }

    @GetMapping("/lab/explain")
    public Map<String, Object> explain() {
        return repository.explain();
    }

    @GetMapping("/meetings/{id}/attendees")
    public QueryReport audience(@PathVariable long id, @RequestParam(defaultValue = "serial") String mode) {
        if (!Set.of("serial", "batch").contains(mode)) throw new IllegalArgumentException("mode只能serial或batch");
        return meetings.audience(id, mode.equals("batch"));
    }

    @PostMapping("/meetings/{id}/notifications/{mode}")
    public NotifyReport send(@PathVariable long id, @PathVariable String mode,
                             @RequestParam(defaultValue = "false") boolean injectFailure, @RequestParam(defaultValue = "false") boolean persist) throws InterruptedException {
        if (!Set.of("serial", "parallel").contains(mode))
            throw new IllegalArgumentException("mode只能serial或parallel");
        if (mode.equals("serial") && injectFailure) throw new IllegalArgumentException("故障实验请使用parallel");
        return meetings.send(id, mode.equals("parallel"), injectFailure, persist);
    }

    @PostMapping("/meetings/{id}/retry")
    public NotifyReport retry(@PathVariable long id) throws InterruptedException {
        return meetings.retry(id);
    }

    @GetMapping("/meetings/{id}/logs")
    public List<Map<String, Object>> logs(@PathVariable long id) {
        return repository.logs(id);
    }

    @GetMapping("/lab/pool")
    public Map<String, Object> pool() {
        return meetings.poolStats();
    }

    @PostMapping("/lab/saturation")
    public Map<String, Object> saturation() throws Exception {
        return meetings.saturation();
    }

    @PostMapping("/lab/log-benchmark")
    public List<Map<String, Object>> benchmark() throws Exception {
        return experiments.benchmark();
    }

    @PostMapping("/lab/locks")
    public Map<String, Object> locks() throws Exception {
        return experiments.locks();
    }
}
