-- 建库 + 建表 + 造 50 万行合同台账数据
CREATE DATABASE IF NOT EXISTS interview_lab DEFAULT CHARACTER SET utf8mb4;

USE interview_lab;

SET SESSION cte_max_recursion_depth = 1000000;

DROP TABLE IF EXISTS contract_ledger;

CREATE TABLE contract_ledger (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    contract_code VARCHAR(32)   NOT NULL,
    title         VARCHAR(128)  NOT NULL,
    contract_type TINYINT       NOT NULL,
    dept_id       INT           NOT NULL,
    creator_id    INT           NOT NULL,
    sign_date     DATE          NOT NULL,
    status        TINYINT       NOT NULL,
    amount        DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO contract_ledger
    (contract_code, title, contract_type, dept_id, creator_id, sign_date, status, amount)
SELECT
    CONCAT('HT', LPAD(i, 8, '0')),
    CONCAT('合同-', i),
    (i % 5) + 1,
    (i % 120) + 1,
    (i % 800) + 1,
    DATE_ADD('2020-01-01', INTERVAL (i % 2500) DAY),
    ((i DIV 120) % 4) + 1,
    (i % 900) * 137.5
FROM (
    WITH RECURSIVE seq(i) AS (
        SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < 500000
    )
    SELECT i FROM seq
) AS s;

ANALYZE TABLE contract_ledger;

SELECT COUNT(*) AS 总行数 FROM contract_ledger;

SELECT COUNT(*) AS 符合查询条件的行数
FROM contract_ledger
WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01';
