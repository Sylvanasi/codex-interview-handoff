#!/usr/bin/env bash
# MySQL 索引实验：一键起容器 → 造数 → 前后对比执行计划。
set -euo pipefail

cd "$(dirname "$0")"

CONTAINER="interview-mysql"
PASSWORD="interview123"
DB="interview_lab"

mysql_exec() {
  docker exec -i "$CONTAINER" mysql -uroot -p"$PASSWORD" --default-character-set=utf8mb4 "$@"
}

echo "① 启动 MySQL 容器（首次会拉镜像，可能要几分钟）"
docker compose up -d

echo
echo "② 等 MySQL 就绪"
READY=0
for _ in $(seq 1 90); do
  if docker exec "$CONTAINER" mysqladmin ping -uroot -p"$PASSWORD" --silent >/dev/null 2>&1; then
    READY=1
    break
  fi
  printf '.'
  sleep 2
done
if [[ "$READY" -ne 1 ]]; then
  echo
  echo "MySQL 没起来，看日志：docker logs $CONTAINER" >&2
  exit 1
fi
echo "   就绪"

echo
echo "③ 建表并写入 50 万行（第一次约 10-40 秒）"
mysql_exec < sql/01-schema.sql

echo
echo "④ 加索引之前"
mysql_exec --table "$DB" < sql/02-before.sql

echo
echo "⑤ 创建联合索引 (dept_id, status, sign_date)"
mysql_exec --table "$DB" < sql/03-index.sql

echo
echo "⑥ 加索引之后（同一条查询）"
mysql_exec --table "$DB" < sql/04-after.sql

echo
echo "⑦ 五个反例：最左匹配 / 覆盖索引 / 回表 / 函数运算 / LIKE 前缀"
mysql_exec --table "$DB" < sql/05-counter-examples.sql

echo
echo "================================================================"
echo "跑完了。接下来做三件事："
echo "  1. 对照上面的输出，把你看到的 type / key / rows / Extra 抄到笔记里"
echo "  2. 打开 README.md，做里面的三道作业"
echo "  3. 想停掉容器：docker compose stop（数据还在）；彻底删除：docker compose down -v"
