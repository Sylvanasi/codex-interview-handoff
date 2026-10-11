package com.wufeng.lab.config;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.wufeng.lab.idempotent.IdempotentStore;
import com.wufeng.lab.idempotent.InMemoryIdempotentStore;
import com.wufeng.lab.idempotent.RedisIdempotentStore;
import com.wufeng.lab.repository.InMemoryPushRecordRepository;
import com.wufeng.lab.repository.JdbcPushRecordRepository;
import com.wufeng.lab.repository.PushRecordRepository;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 两层幂等设施的装配：按配置决定用"内存实现"还是"真 Redis / 真 MySQL"。
 *
 * 关键点是 @ConditionalOnProperty —— 它是 Spring Boot 自动配置的基石：
 * 同一个接口（IdempotentStore）在容器里只留一个实现，选谁由配置决定，
 * 业务代码只注入接口，完全不知道背后是内存还是 Redis。这就是"面向接口 + 条件装配"。
 *
 * 另外注意本类里手写 DataSource 的那几行：
 * 我们在启动类上把 DataSourceAutoConfiguration 排除了，然后在这里用 DataSourceProperties
 * 自己 new 一个 HikariDataSource。写一遍你就明白自动配置到底帮你做了什么 ——
 * 面试问"Spring Boot 自动配置原理"时，这段可以当成你自己的例子讲。
 */
@Configuration
public class InfraConfig {

    // ==================== 第一层：幂等拦截 ====================

    @Bean
    @ConditionalOnProperty(name = "lab.store", havingValue = "redis")
    public IdempotentStore redisIdempotentStore(StringRedisTemplate stringRedisTemplate, LabProperties props) {
        return new RedisIdempotentStore(stringRedisTemplate, props.getRedisKeyPrefix());
    }

    @Bean
    @ConditionalOnProperty(name = "lab.store", havingValue = "memory", matchIfMissing = true)
    public IdempotentStore inMemoryIdempotentStore() {
        return new InMemoryIdempotentStore();
    }

    // ==================== 数据源（手写，替代被排除的自动配置） ====================

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties springDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConditionalOnProperty(name = "lab.repository", havingValue = "mysql")
    public DataSource dataSource(DataSourceProperties properties) {
        // initializeDataSourceBuilder 会从上面的 properties 里读 url / username / password / driver-class-name
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    // ==================== 第二层：推送台账（最终兜底） ====================

    @Bean
    @ConditionalOnProperty(name = "lab.repository", havingValue = "mysql")
    public PushRecordRepository jdbcPushRecordRepository(JdbcTemplate jdbcTemplate) {
        // JdbcTemplate 由 Spring Boot 的 JdbcTemplateAutoConfiguration 提供，
        // 它的条件是"容器里有一个 DataSource"，也就是上面那个 bean。
        return new JdbcPushRecordRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "lab.repository", havingValue = "memory", matchIfMissing = true)
    public PushRecordRepository inMemoryPushRecordRepository() {
        return new InMemoryPushRecordRepository();
    }

    /**
     * 建表：只在用 MySQL 时执行一次。
     *
     * 为什么不用 Spring Boot 的 spring.sql.init？
     * 因为它的触发条件依赖自动配置链，本项目又把 DataSourceAutoConfiguration 排除了，
     * 与其猜它会不会跑，不如自己用 Spring 提供的 SQL 脚本执行器明确跑一次 ——
     * 生产里更常见的做法是交给 Flyway / Liquibase 这类迁移工具，这里演示原理就够。
     */
    @Bean
    @ConditionalOnProperty(name = "lab.repository", havingValue = "mysql")
    public ApplicationRunner pushRecordSchemaInitializer(DataSource dataSource) {
        return args -> new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
    }
}
