package com.wufeng.lab.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL 版台账。核心就一句：靠数据库唯一索引兜底，而不是靠"先查再插"。
 *
 * 面试最常被追的一问是："你查一下不就知道有没有了？"
 * 答：查和插之间有时间差，两个并发请求会同时查到"没有"，然后同时插、同时推。
 * 唯一索引把这件事交给数据库，由它在写入的瞬间做原子判定，只有一个能成功。
 *
 * 注意 catch 的是 Spring 翻译后的 DuplicateKeyException：
 * 它把各数据库五花八门的约束异常统一成一种（MySQL 1062、Oracle ORA-00001 都会翻译到它），
 * 所以业务代码不用去认数据库厂商的错误码。
 */
public class JdbcPushRecordRepository implements PushRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcPushRecordRepository.class);

    private final JdbcTemplate jdbc;

    public JdbcPushRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(String bizId) {
        try {
            jdbc.update("INSERT INTO push_record (biz_id, state, updated_at) VALUES (?, ?, ?)",
                    bizId, IN_PROGRESS, Timestamp.from(Instant.now()));
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一索引挡住了：说明这张单已经在台账里（正在推 / 推过了）
            log.info("数据库台账已存在 biz_id={}，判定为重复，不再推送", bizId);
            return false;
        }
    }

    @Override
    public void markDone(String bizId) {
        jdbc.update("UPDATE push_record SET state = ?, reason = NULL, updated_at = ? WHERE biz_id = ?",
                DONE, Timestamp.from(Instant.now()), bizId);
    }

    @Override
    public void markFailed(String bizId, String reason) {
        jdbc.update("UPDATE push_record SET state = ?, reason = ?, updated_at = ? WHERE biz_id = ?",
                FAILED, reason, Timestamp.from(Instant.now()), bizId);
    }

    @Override
    public boolean exists(String bizId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM push_record WHERE biz_id = ?", Integer.class, bizId);
        return count != null && count > 0;
    }

    @Override
    public List<String> findStuck(int stuckMinutes) {
        // 把时间阈值在 Java 里算好再传进去：DATE_SUB() 这类函数各家数据库语法不同，
        // 用标准比较符加参数，SQL 才能既跑 MySQL 又跑 H2。
        Timestamp deadline = Timestamp.from(Instant.now().minus(stuckMinutes, ChronoUnit.MINUTES));
        return jdbc.queryForList(
                "SELECT biz_id FROM push_record WHERE state = ? AND updated_at < ?",
                String.class, IN_PROGRESS, deadline);
    }

    @Override
    public int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM push_record", Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<Map<String, Object>> listAll() {
        return jdbc.queryForList(
                "SELECT biz_id AS bizId, state, reason, updated_at AS updatedAt "
                        + "FROM push_record ORDER BY updated_at DESC LIMIT 100");
    }

    @Override
    public String backend() {
        return "mysql(push_record)";
    }

    @Override
    public void clear() {
        jdbc.update("DELETE FROM push_record");
    }
}
