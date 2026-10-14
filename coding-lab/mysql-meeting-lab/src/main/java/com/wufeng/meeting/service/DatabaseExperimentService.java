package com.wufeng.meeting.service;

import com.wufeng.meeting.model.Models.Outcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

@Service
public class DatabaseExperimentService {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final LearningSteps steps;

    public DatabaseExperimentService(JdbcTemplate jdbc, DataSource dataSource, LearningSteps steps) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.steps = steps;
    }

    private static final String INSERT = "INSERT INTO push_log(meeting_id,person_id,status) VALUES(?,?,'SUCCESS')";

    private double ms(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    public List<Map<String, Object>> benchmark() throws Exception {
        var items = LongStream.rangeClosed(1, 300).mapToObj(p -> new Outcome(p, "SUCCESS", 1)).toList();
        List<Map<String, Object>> rounds = new ArrayList<>();
        for (int round = 1; round <= 3; round++) {
            jdbc.update("DELETE FROM push_log WHERE meeting_id IN(20,21,22)");
            long start = System.nanoTime();
            for (var o : items) jdbc.update(INSERT, 20, o.personId());
            double single = ms(start);
            start = System.nanoTime();
            ExecutorService writers = Executors.newFixedThreadPool(8);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int k = 0; k < 8; k++) {
                    final int offset = k;
                    futures.add(writers.submit(() -> {
                        for (int i = offset; i < items.size(); i += 8) jdbc.update(INSERT, 21, items.get(i).personId());
                    }));
                }
                for (var f : futures) f.get();
            } finally {
                writers.shutdownNow();
                if (!writers.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("日志线程未终止");
            }
            double concurrent = ms(start);
            start = System.nanoTime();
            steps.writeBatch(items, 22);
            double batch = ms(start);
            var counts = jdbc.queryForList("SELECT meeting_id,COUNT(*) AS total FROM push_log WHERE meeting_id IN(20,21,22) GROUP BY meeting_id ORDER BY meeting_id");
            if (counts.size() != 3 || counts.stream().anyMatch(row -> ((Number) row.get("total")).intValue() != 300))
                throw new IllegalStateException("写入数量不正确");
            rounds.add(Map.of("round", round, "singleMs", single, "concurrentMs", concurrent, "batchMs", batch, "counts", counts));
        }
        return rounds;
    }

    public Map<String, Object> locks() throws Exception {
        jdbc.update("DELETE FROM push_log WHERE meeting_id=99");
        jdbc.update("INSERT INTO push_log(meeting_id,person_id,status) VALUES(99,1,'SUCCESS'),(99,2,'SUCCESS')");
        CountDownLatch held = new CountDownLatch(2);
        AtomicInteger deadlocks = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int order = 0; order < 2; order++) {
                final int first = order + 1, second = 2 - order;
                futures.add(workers.submit(() -> {
                    try (var c = dataSource.getConnection()) {
                        c.setAutoCommit(false);
                        try (var p = c.prepareStatement("UPDATE push_log SET status='LOCK_TEST' WHERE meeting_id=99 AND person_id=?")) {
                            p.setInt(1, first);
                            p.executeUpdate();
                            held.countDown();
                            if (!held.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("同步锁超时");
                            p.setInt(1, second);
                            p.executeUpdate();
                            c.commit();
                        } catch (SQLException e) {
                            c.rollback();
                            if (e.getErrorCode() == 1213) deadlocks.incrementAndGet();
                            else throw e;
                        } catch (Exception e) {
                            c.rollback();
                            throw e;
                        }
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }));
            }
            for (var f : futures) f.get(15, TimeUnit.SECONDS);
            if (deadlocks.get() != 1) throw new IllegalStateException("没有复现一次1213，请检查innodb_deadlock_detect");
            var monitor = jdbc.queryForMap("SHOW ENGINE INNODB STATUS");
            String text = String.valueOf(monitor.get("Status"));
            int a = text.indexOf("LATEST DETECTED DEADLOCK"), b = text.indexOf("\nTRANSACTIONS\n", a);
            return Map.of("deadlocks", deadlocks.get(), "diagnostic", a >= 0 ? text.substring(a, b > a ? b : text.length()) : "未找到死锁片段");
        } finally {
            workers.shutdownNow();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("死锁实验线程未终止");
        }
    }
}
