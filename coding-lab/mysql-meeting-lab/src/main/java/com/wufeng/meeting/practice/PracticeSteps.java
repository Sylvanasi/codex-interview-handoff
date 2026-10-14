package com.wufeng.meeting.practice;

import com.wufeng.meeting.model.Models.*;
import com.wufeng.meeting.service.*;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 只改此类的七个TODO；框架可以正常启动，未完成的接口返回501提示。
 */
@Component
@Profile("!answer")
public class PracticeSteps implements LearningSteps {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MockNotificationClient client;

    public PracticeSteps(JdbcTemplate jdbc, TransactionTemplate transactions, MockNotificationClient client) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.client = client;
    }

    // TODO-1：jdbc.execute执行topic_id普通索引DDL，再ANALYZE TABLE。
    public void addTopicIndex() {
        jdbc.execute(
                "ALTER TABLE topic_attendee ADD INDEX idx_topic (topic_id)"
        );
        jdbc.execute("ANALYZE TABLE topic_attendee");

    }

    // TODO-2：500个topic_id一批，?绑定，Set去重，sqlCount只计本方法查询次数。
    public Audience loadBatch(List<Long> topics) {
        Set<Long> people = new TreeSet<>();
        int sqlCount = 0;
        for(int from=0;from<topics.size();from+=500){
            List<Long> part=topics.subList(from,Math.min(from+500,topics.size()));
            String marks=String.join(",",Collections.nCopies(part.size(),"?"));
            people.addAll(jdbc.queryForList("SELECT person_id FROM topic_attendee WHERE topic_id IN ("+marks+")",Long.class,part.toArray()));
            sqlCount++;
        }
        return new Audience(people,sqlCount);
    }

    // TODO-3：手写七参数；有界capacity队列、命名线程、计数后抛拒绝异常。
    public ThreadPoolExecutor createPool(int core, int max, int capacity, AtomicInteger rejected) {
        AtomicInteger seq=new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(core, max, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                (r) -> new Thread(r, "meeting-notify-" + seq.getAndIncrement()),
                (task,excutor) -> {
                    rejected.incrementAndGet();
                    throw  new RejectedExecutionException("会议通知队列已满");
                });
        return pool;
    }

    // TODO-4：调用client.send，只捕获RuntimeException转FAILED；让中断向上传递。
    public Outcome safeSend(long person, int attempt, boolean inject) throws InterruptedException {
        try {
            return client.send(person, attempt, inject);
        }catch (RuntimeException e){
            return new Outcome(person,"FAILED",attempt);
        }
    }

    // TODO-5：先提交再收集；提交拒绝生成REJECTED；中断取消本次请求任务并恢复标志。
    // pool是Spring共享Bean，不能在请求结束时shutdown！
    public List<Outcome> parallel(Collection<Long> people, int attempt, boolean inject, ThreadPoolExecutor pool) throws InterruptedException {
        List<Outcome> outcomes = new ArrayList<>();
        Map<Long, Future<Outcome>> futures = new ConcurrentHashMap<>();
        try {

            for (var peopleId : people) {
                try {
                    futures.put(peopleId, pool.submit(() -> safeSend(peopleId, attempt, inject))); }
                catch (RejectedExecutionException e) {
                    outcomes.add(new Outcome(peopleId, "REJECTED", attempt));
                }
            }

            for (Future<Outcome> future : futures.values()) {
                try {
                    outcomes.add(future.get());
                } catch(ExecutionException e){
                    throw new IllegalStateException(
                            "通知任务执行异常",
                            e.getCause()
                    );
                }
            }
        }catch(InterruptedException e){
            //停止当前任务
            futures.values().forEach(f -> {
                f.cancel(true);
            });
            pool.purge();
            Thread.currentThread().interrupt();
            throw e;
        } catch (RuntimeException e) {
            futures.values().forEach(f -> f.cancel(true));
            pool.purge();
            throw e;
        }

        return outcomes;
    }

    // TODO-6：只查指定会议status=FAILED的person_id。
    public Set<Long> loadFailedPeople(long meeting) {
        throw todo(6);
    }

    // TODO-7：每100条多VALUES写入；用transactions.executeWithoutResult，每批一个事务。
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

    private UnsupportedOperationException todo(int n) {
        return new UnsupportedOperationException("请完成PracticeSteps TODO-" + n + "；见动手练习.md");
    }
}
