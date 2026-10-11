package com.wufeng.lab;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.wufeng.lab.repository.PushRecordRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库台账的验收。
 *
 * 这里用 H2（MySQL 兼容模式）而不是真的 MySQL，原因有两个：
 * 1. 单元测试不该依赖"本机装没装 MySQL"，否则换台机器就跑不了 —— 这叫测试的可移植性；
 * 2. 我们验证的是"唯一约束会不会挡住第二次插入"这件事本身，
 *    H2 和 MySQL 在这件事上的行为一致（第二次 INSERT 直接报错）。
 *    跟数据库特性强相关的部分（比如锁、执行计划）才必须上真 MySQL。
 *
 * 配置里做了三件事：把台账切到 mysql 实现、把数据源指向 H2、让 schema.sql 自动建表。
 * 这三行也是你在真实项目里做 DAO 层测试的标准套路。
 */
@SpringBootTest(properties = {
        "lab.repository=mysql",
        "spring.datasource.url=jdbc:h2:mem:pushlab;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class PushRecordJdbcTest {

    @Autowired
    private PushRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        repository.clear();
    }

    @Test
    @DisplayName("同一张单插两次：第二次被唯一索引挡住")
    void uniqueIndexBlocksSecondInsert() {
        assertThat(repository.insertIfAbsent("HT-20250001")).isTrue();
        assertThat(repository.insertIfAbsent("HT-20250001")).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("状态流转：进行中 → 已完成")
    void stateFlow() {
        repository.insertIfAbsent("HT-20250002");
        List<String> stuckImmediately = repository.findStuck(5);
        assertThat(stuckImmediately).doesNotContain("HT-20250002");   // 刚插入，不算卡住

        repository.markDone("HT-20250002");
        List<java.util.Map<String, Object>> rows = repository.listAll();
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("state")).intValue()).isEqualTo(PushRecordRepository.DONE);
    }

    @Test
    @DisplayName("对账扫描：只捞出卡在进行中且超时的记录")
    void findStuckOnlyReturnsOverdueInProgress() {
        repository.insertIfAbsent("HT-STUCK");
        repository.insertIfAbsent("HT-OK");
        repository.markDone("HT-OK");

        // 手工把 HT-STUCK 的更新时间改到 30 分钟前，模拟"推完没收到回执就断了"
        jdbcTemplate.update("UPDATE push_record SET updated_at = ? WHERE biz_id = ?",
                Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)), "HT-STUCK");

        List<String> stuck = repository.findStuck(5);
        assertThat(stuck).containsExactly("HT-STUCK");   // 已完成的 HT-OK 不该被捞出来
    }
}
