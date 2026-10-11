#!/usr/bin/env bash
# 索引与慢 SQL 实验：只需要 sqlite3（macOS 自带，无需安装 MySQL）。
set -euo pipefail

cd "$(dirname "$0")"
DB="contract_lab.db"
rm -f "$DB"

QUERY="SELECT count(*) FROM (SELECT id, contract_code, title, amount, sign_date, status FROM contract_ledger WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01' ORDER BY sign_date DESC LIMIT 20);"
COUNT_QUERY="SELECT count(*) FROM contract_ledger WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01';"

echo "① 建表并写入 50 万行合同数据……"
sqlite3 "$DB" < setup.sql
echo "   完成，共 $(sqlite3 "$DB" 'SELECT count(*) FROM contract_ledger;') 行"
echo "   其中符合条件的：$(sqlite3 "$DB" "$COUNT_QUERY") 行（这就是索引要帮你少扫多少的量级）"

run_query() {
  echo "  计划："
  sqlite3 "$DB" "EXPLAIN QUERY PLAN $QUERY" | sed 's/^/    /'
  echo "  执行："
  sqlite3 "$DB" 2>&1 <<SQL | sed 's/^/    /'
.timer on
$QUERY
SQL
}

echo
echo "② 加索引之前（查询条件：部门 + 状态 + 时间范围，按签署时间倒序取前 20 条）"
run_query

echo
echo "③ 创建联合索引 (dept_id, status, sign_date)"
sqlite3 "$DB" "CREATE INDEX idx_ledger_dept_status_date ON contract_ledger(dept_id, status, sign_date);"
sqlite3 "$DB" "ANALYZE;"

echo
echo "④ 加索引之后（同一条查询）"
run_query

echo
echo "⑤ 反例一：条件列根本不在索引里（creator_id）——只能全表扫"
sqlite3 "$DB" "EXPLAIN QUERY PLAN SELECT sum(amount) FROM contract_ledger WHERE creator_id = 500;" | sed 's/^/    /'

echo
echo "⑥ 反例二：只查索引里有的列（覆盖索引，不用回表）"
sqlite3 "$DB" "EXPLAIN QUERY PLAN SELECT dept_id, status, sign_date FROM contract_ledger WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01';" | sed 's/^/    /'

echo
echo "⑦ 反例三：多查一个 title（需要回表）"
sqlite3 "$DB" "EXPLAIN QUERY PLAN SELECT dept_id, status, sign_date, title FROM contract_ledger WHERE dept_id = 37 AND status = 2 AND sign_date >= '2025-01-01';" | sed 's/^/    /'

echo
echo "⑧ 注意：sqlite 对「只按 status 查」这种缺最左列的情况会做 skip-scan 补救，"
echo "   MySQL 没有这个优化，缺最左列就是实打实的 type=ALL 全表扫描。"
echo "   所以最左匹配这一条，以 MySQL 版实验（coding-lab/mysql-index-lab）为准。"
echo
echo "⑨ 作业：把 ⑤ 改成能走索引的写法（提示：条件里补上 dept_id），再跑一次看计划变化。"
