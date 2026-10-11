package com.wufeng.lab.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版台账：用 putIfAbsent 模拟数据库唯一索引。
 * 好处是零依赖就能跑通整套流程；坏处是重启即丢，正好能用来演示"兜底层挂了会怎样"。
 */
public class InMemoryPushRecordRepository implements PushRecordRepository {

    private record Record(int state, String reason, Instant updatedAt) {
        Record with(int newState, String newReason) {
            return new Record(newState, newReason, Instant.now());
        }
    }

    private final Map<String, Record> store = new ConcurrentHashMap<>();

    @Override
    public boolean insertIfAbsent(String bizId) {
        // 等价于 INSERT 撞 UNIQUE(biz_id)：putIfAbsent 返回 null 才算插入成功
        return store.putIfAbsent(bizId, new Record(IN_PROGRESS, null, Instant.now())) == null;
    }

    @Override
    public void markDone(String bizId) {
        store.computeIfPresent(bizId, (k, v) -> v.with(DONE, null));
    }

    @Override
    public void markFailed(String bizId, String reason) {
        store.computeIfPresent(bizId, (k, v) -> v.with(FAILED, reason));
    }

    @Override
    public boolean exists(String bizId) {
        return store.containsKey(bizId);
    }

    @Override
    public List<String> findStuck(int stuckMinutes) {
        Instant deadline = Instant.now().minus(stuckMinutes, ChronoUnit.MINUTES);
        List<String> stuck = new ArrayList<>();
        store.forEach((bizId, record) -> {
            if (record.state() == IN_PROGRESS && record.updatedAt().isBefore(deadline)) {
                stuck.add(bizId);
            }
        });
        return stuck;
    }

    @Override
    public int count() {
        return store.size();
    }

    @Override
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> rows = new ArrayList<>();
        store.forEach((bizId, record) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("bizId", bizId);
            row.put("state", record.state());
            row.put("reason", record.reason());
            row.put("updatedAt", record.updatedAt().toString());
            rows.add(row);
        });
        return rows;
    }

    @Override
    public String backend() {
        return "memory(ConcurrentHashMap)";
    }

    @Override
    public void clear() {
        store.clear();
    }
}
