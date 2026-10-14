package com.wufeng.meeting.answer;

import com.wufeng.meeting.model.Models.*;
import com.wufeng.meeting.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("answer")
public class ReferenceSteps implements LearningSteps {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MockNotificationClient client;

    public ReferenceSteps(JdbcTemplate jdbc, TransactionTemplate transactions, MockNotificationClient client) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.client = client;
    }

    public void addTopicIndex() {
        jdbc.execute("ALTER TABLE topic_attendee ADD INDEX idx_topic(topic_id)");
        jdbc.execute("ANALYZE TABLE topic_attendee");
    }

    public Audience loadBatch(List<Long> topics) {
        Set<Long> people = new TreeSet<>();
        int count = 0;
        for (int from = 0; from < topics.size(); from += 500) {
            var part = topics.subList(from, Math.min(from + 500, topics.size()));
            String marks = String.join(",", Collections.nCopies(part.size(), "?"));
            people.addAll(jdbc.queryForList("SELECT person_id FROM topic_attendee WHERE topic_id IN (" + marks + ")", Long.class, part.toArray()));
            count++;
        }
        return new Audience(people, count);
    }

    public ThreadPoolExecutor createPool(int core, int max, int capacity, AtomicInteger rejected) {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(core, max, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity),
                r -> new Thread(r, "meeting-notify-" + seq.incrementAndGet()), (r, e) -> {
            rejected.incrementAndGet();
            throw new RejectedExecutionException("会议通知队列已满");
        });
    }

    public Outcome safeSend(long person, int attempt, boolean inject) throws InterruptedException {
        try {
            return client.send(person, attempt, inject);
        } catch (RuntimeException e) {
            return new Outcome(person, "FAILED", attempt);
        }
    }

    public List<Outcome> parallel(Collection<Long> people, int attempt, boolean inject, ThreadPoolExecutor pool) throws InterruptedException {
        Map<Long, Future<Outcome>> tasks = new LinkedHashMap<>();
        List<Outcome> results = new ArrayList<>();
        try {
            for (long person : people) {
                try {
                    tasks.put(person, pool.submit(() -> safeSend(person, attempt, inject)));
                } catch (RejectedExecutionException e) {
                    results.add(new Outcome(person, "REJECTED", attempt));
                }
            }
            for (var entry : tasks.entrySet()) {
                try {
                    results.add(entry.getValue().get());
                } catch (ExecutionException e) {
                    throw new IllegalStateException("通知任务非业务异常", e.getCause());
                }
            }
            return results;
        } catch (InterruptedException e) {
            tasks.values().forEach(f -> f.cancel(true));
            pool.purge();
            Thread.currentThread().interrupt();
            throw e;
        } catch (RuntimeException e) {
            tasks.values().forEach(f -> f.cancel(true));
            pool.purge();
            throw e;
        }
        // 共享线程池由Spring管理；不在请求finally里关闭。
    }

    public Set<Long> loadFailedPeople(long meeting) {
        return new TreeSet<>(jdbc.queryForList("SELECT person_id FROM push_log WHERE meeting_id=? AND status='FAILED'", Long.class, meeting));
    }

    public void writeBatch(List<Outcome> outcomes, long meeting) {
        for (int from = 0; from < outcomes.size(); from += 100) {
            var part = outcomes.subList(from, Math.min(from + 100, outcomes.size()));
            String values = String.join(",", Collections.nCopies(part.size(), "(?,?,?)"));
            List<Object> args = new ArrayList<>();
            for (var o : part) {
                args.add(meeting);
                args.add(o.personId());
                args.add(o.status());
            }
            // TransactionTemplate在异常时回滚本批；避免同类调用@Transactional的代理陷阱。
            transactions.executeWithoutResult(status -> jdbc.update(
                    "INSERT INTO push_log(meeting_id,person_id,status) VALUES " + values +
                            " AS incoming ON DUPLICATE KEY UPDATE status=incoming.status,create_time=CURRENT_TIMESTAMP", args.toArray()));
        }
    }
}
