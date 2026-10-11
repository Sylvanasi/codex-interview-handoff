USE interview_lab;

SELECT '===== 加索引前：执行计划（重点看 type / key / rows / Extra）=====' AS note;

EXPLAIN
SELECT id, contract_code, title, amount, sign_date, status
FROM contract_ledger
WHERE dept_id = 37
  AND status = 2
  AND sign_date >= '2025-01-01'
ORDER BY sign_date DESC
LIMIT 20;

SELECT '===== 加索引前：实际执行数据（MySQL 8.0.18+ 支持 EXPLAIN ANALYZE）=====' AS note;

EXPLAIN ANALYZE
SELECT id, contract_code, title, amount, sign_date, status
FROM contract_ledger
WHERE dept_id = 37
  AND status = 2
  AND sign_date >= '2025-01-01'
ORDER BY sign_date DESC
LIMIT 20;
