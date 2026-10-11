-- 推送台账：幂等的最终兜底层
-- 表结构刻意写得简单，唯一的关键就是最后那行 UNIQUE (biz_id)。
--
-- 为什么不用 ENGINE=InnoDB / DEFAULT CHARSET 这类 MySQL 专属语法？
-- 因为同一份脚本还要能在 H2（测试用）里跑，SQL 保持标准写法最省事。
CREATE TABLE IF NOT EXISTS push_record (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    biz_id     VARCHAR(64)  NOT NULL,
    state      TINYINT      NOT NULL,
    reason     VARCHAR(255),
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_push_record_biz_id UNIQUE (biz_id)
);

-- 索引说明（面试点）：
-- UNIQUE (biz_id) 既是唯一约束又是一个索引，所以按 biz_id 查询也会走它。
-- 对账任务按 (state, updated_at) 扫描，数据量大时可以再加一个联合索引：
-- CREATE INDEX idx_push_record_state_time ON push_record (state, updated_at);
