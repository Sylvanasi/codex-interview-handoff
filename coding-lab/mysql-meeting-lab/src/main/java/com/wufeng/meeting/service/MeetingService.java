package com.wufeng.meeting.service;

import com.wufeng.meeting.model.Models.*;
import com.wufeng.meeting.repository.MeetingRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.wufeng.meeting.model.Models.report;

@Service
public class MeetingService {
    private final MeetingRepository repository;
    private final LearningSteps steps;
    private final MockNotificationClient client;
    private final ObjectProvider<ThreadPoolExecutor> pool;
    private final AtomicInteger rejected;

    public MeetingService(MeetingRepository repository, LearningSteps steps, MockNotificationClient client,
                          ObjectProvider<ThreadPoolExecutor> pool, AtomicInteger rejected) {
        this.repository = repository;
        this.steps = steps;
        this.client = client;
        this.pool = pool;
        this.rejected = rejected;
    }

    public QueryReport audience(long meeting, boolean batch) {
        long start = System.nanoTime();
        var topics = repository.topics(meeting);
        if (topics.isEmpty()) throw new IllegalArgumentException("会议没有议题，请先初始化并使用会议1");
        Audience a = batch ? steps.loadBatch(topics) : repository.serial(topics);
        return new QueryReport(a.people(), a.people().size(), a.sqlCount() + 1, (System.nanoTime() - start) / 1_000_000.0);
    }

    public NotifyReport send(long meeting, boolean concurrent, boolean inject, boolean persist) throws InterruptedException {
        // 查人阶段单独计时；通知报告不混入SQL耗时。串行基线不依赖TODO-2。
        var people = audience(meeting, concurrent).people();
        long start = System.nanoTime();
        List<Outcome> outcomes = new ArrayList<>();
        if (concurrent) outcomes = steps.parallel(people, 1, inject, pool.getObject());
        else for (long person : people) outcomes.add(client.send(person, 1, false));
        if (persist) steps.writeBatch(outcomes, meeting);
        return report(outcomes, start);
    }

    public NotifyReport retry(long meeting) throws InterruptedException {
        Set<Long> failed = steps.loadFailedPeople(meeting);
        long start = System.nanoTime();
        if (failed.isEmpty()) return report(List.of(), start);
        var outcomes = steps.parallel(failed, 2, true, pool.getObject());
        steps.writeBatch(outcomes, meeting);
        return report(outcomes, start);
    }

    public Map<String, Object> poolStats() {
        var p = pool.getObject();
        return Map.of("active", p.getActiveCount(), "poolSize", p.getPoolSize(),
                "queued", p.getQueue().size(), "completed", p.getCompletedTaskCount(), "rejected", rejected.get());
    }

    public Map<String, Object> saturation() throws Exception {
        AtomicInteger count = new AtomicInteger();
        var dedicated = steps.createPool(1, 1, 1, count);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<Future<Outcome>> accepted = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();
        Set<String> workerNames = ConcurrentHashMap.newKeySet();
        String caller = Thread.currentThread().getName();
        try {
            accepted.add(dedicated.submit(() -> {
                workerNames.add(Thread.currentThread().getName());
                entered.countDown();
                release.await();
                return steps.safeSend(1, 1, false);
            }));
            if (!entered.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("首任务未启动");
            for (long person = 2; person <= 20; person++) {
                final long id = person;
                try {
                    accepted.add(dedicated.submit(() -> {
                        workerNames.add(Thread.currentThread().getName());
                        return steps.safeSend(id, 1, false);
                    }));
                } catch (RejectedExecutionException e) {
                    outcomes.add(new Outcome(id, "REJECTED", 1));
                }
            }
            release.countDown();
            for (var f : accepted) outcomes.add(f.get(3, TimeUnit.SECONDS));
            return Map.of("results", outcomes, "rejected", count.get(), "caller", caller, "workers", workerNames,
                    "callerExecutedNotification", workerNames.contains(caller));
        } finally {
            release.countDown();
            dedicated.shutdownNow();
            if (!dedicated.awaitTermination(3, TimeUnit.SECONDS)) throw new IllegalStateException("测试池未终止");
        }
    }
}
