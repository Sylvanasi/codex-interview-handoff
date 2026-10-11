USE interview_lab;

SELECT '===== 加索引后：执行计划（type 应变 ref/range，key 出现索引名，Extra 里 filesort 消失）=====' AS note;

EXPLAIN
SELECT id, contract_code, title, amount, sign_date, status
FROM contract_ledger
WHERE dept_id = 37
  AND status = 2
  AND sign_date >= '2025-01-01'
ORDER BY sign_date DESC
LIMIT 20;

SELECT '===== 加索引后：实际执行数据 =====' AS note;

EXPLAIN ANALYZE
SELECT id, contract_code, title, amount, sign_date, status
FROM contract_ledger
WHERE dept_id = 37
  AND status = 2
  AND sign_date >= '2025-01-01'
ORDER BY sign_date DESC
LIMIT 20;
