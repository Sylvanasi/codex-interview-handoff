#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
container="${MYSQL_CONTAINER:-mysql}"
user="${MYSQL_USER:-root}"
read -r -s -p "MySQL password: " lab_password
printf '\n'
trap 'unset lab_password' EXIT
mkdir -p 实测/最近运行
for script in sql/01-schema-data.sql sql/02-baseline.sql sql/03-index-comparison.sql sql/04-pagination.sql; do
  name="$(basename "$script" .sql)"
  docker exec -i -e MYSQL_PWD="$lab_password" "$container" mysql -u"$user" < "$script" | tee "实测/最近运行/$name.txt"
done
