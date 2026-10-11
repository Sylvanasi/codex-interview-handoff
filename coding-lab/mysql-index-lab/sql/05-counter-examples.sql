USE interview_lab;

SELECT '===== 反例 1：缺最左列 dept_id，只能全扫（对比下一条）=====' AS note;
EXPLAIN SELECT id FROM contract_ledger WHERE status = 2;

SELECT '===== 对照：补上最左列 dept_id，立刻走索引 =====' AS note;
EXPLAIN SELECT id FROM contract_ledger WHERE dept_id = 37 AND status = 2;

SELECT '===== 反例 2：覆盖索引（要的列都在索引里，Extra 出现 Using index）=====' AS note;
EXPLAIN
SELECT dept_id, status, sign_date
FROM contract_ledger
WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01';

SELECT '===== 反例 3：需要回表（多查 title，Using index 消失）=====' AS note;
EXPLAIN
SELECT dept_id, status, sign_date, title
FROM contract_ledger
WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01';

SELECT '===== 反例 4：对索引列做函数运算，索引失效 =====' AS note;
EXPLAIN SELECT id FROM contract_ledger WHERE YEAR(sign_date) = 2025;

SELECT '===== 对照：改成范围条件，索引可用 =====' AS note;
EXPLAIN SELECT id FROM contract_ledger WHERE sign_date >= '2025-01-01' AND sign_date < '2026-01-01';

SELECT '===== 反例 5：LIKE 以 % 开头，索引失效 =====' AS note;
EXPLAIN SELECT id FROM contract_ledger WHERE title LIKE '%合同-1%';

SELECT '===== 对照：前缀匹配，索引可用 =====' AS note;
EXPLAIN SELECT id FROM contract_ledger WHERE title LIKE '合同-1%';
