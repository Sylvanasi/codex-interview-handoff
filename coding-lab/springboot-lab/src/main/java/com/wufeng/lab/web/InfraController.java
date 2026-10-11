package com.wufeng.lab.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wufeng.lab.idempotent.IdempotentStore;
import com.wufeng.lab.repository.PushRecordRepository;

/**
 * 环境自检接口：一眼看清"我现在到底连的是内存还是真 Redis、真 MySQL"。
 *
 * 别小看这个接口。真实项目里最常见的低级事故就是"以为连的是测试库，其实连的是生产库"，
 * 所以上线检查清单里通常都有一条：启动后打一次中间件连通性探活。
 */
@RestController
@RequestMapping("/api/infra")
public class InfraController {

    private final IdempotentStore store;
    private final PushRecordRepository repository;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public InfraController(IdempotentStore store,
                           PushRecordRepository repository,
                           ObjectProvider<StringRedisTemplate> redisProvider,
                           ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.store = store;
        this.repository = repository;
        this.redisProvider = redisProvider;
        this.jdbcProvider = jdbcProvider;
    }

    private String pingRedis() {
        StringRedisTemplate template = redisProvider.getIfAvailable();
        if (template == null) {
            return "未装配（当前用的不是 Redis）";
        }
        try {
            return template.execute((RedisCallback<String>) connection -> connection.ping());
        } catch (RuntimeException e) {
            return "连不上：" + e.getMessage();
        }
    }

    @GetMapping
    public Map<String, Object> infra() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idempotentStore", store.backend());
        result.put("pushRecordRepository", repository.backend());
        result.put("redisPing", pingRedis());
        result.put("mysql", mysqlInfo());
        result.put("pushRecordCount", countRecords());
        return result;
    }

    private String mysqlInfo() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return "未装配（当前用的不是 MySQL）";
        }
        try {
            String version = jdbc.queryForObject("SELECT VERSION()", String.class);
            String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
            return version + " / 当前库=" + database;
        } catch (RuntimeException e) {
            return "连不上：" + e.getMessage();
        }
    }

    private Object countRecords() {
        try {
            return repository.count();
        } catch (RuntimeException e) {
            return "查询失败：" + e.getMessage();
        }
    }
}
