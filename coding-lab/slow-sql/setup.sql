-- 合同台账表：模拟一张"列表查询很慢"的业务表，50 万行
DROP TABLE IF EXISTS contract_ledger;

CREATE TABLE contract_ledger (
    id            INTEGER PRIMARY KEY,
    contract_code TEXT    NOT NULL,
    title         TEXT    NOT NULL,
    contract_type INTEGER NOT NULL,
    dept_id       INTEGER NOT NULL,
    creator_id    INTEGER NOT NULL,
    sign_date     TEXT    NOT NULL,
    status        INTEGER NOT NULL,
    amount        REAL    NOT NULL
);

INSERT INTO contract_ledger (contract_code, title, contract_type, dept_id, creator_id, sign_date, status, amount)
SELECT
    'HT' || printf('%08d', i),
    '合同-' || i,
    (i % 5) + 1,
    (i % 120) + 1,
    (i % 800) + 1,
    date('2020-01-01', '+' || (i % 2500) || ' days'),
    ((i / 120) % 4) + 1,
    (i % 900) * 137.5
FROM (
    WITH RECURSIVE seq(i) AS (
        SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < 500000
    )
    SELECT i FROM seq
);
