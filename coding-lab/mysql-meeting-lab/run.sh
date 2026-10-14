#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
PROFILE=practice
if [[ "${1:-}" == "--answer" ]]; then PROFILE=answer; shift; fi
MVN_BIN="${MVN_BIN:-mvn}"
if ! command -v "$MVN_BIN" >/dev/null 2>&1; then
  MVN_BIN='/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn'
fi
if [[ ! -x "$MVN_BIN" ]] && ! command -v "$MVN_BIN" >/dev/null 2>&1; then echo '请安装Maven或设置MVN_BIN。' >&2; exit 1; fi
if [[ -z "${MYSQL_PASSWORD:-}" ]]; then read -r -s -p 'MySQL 密码: ' MYSQL_PASSWORD; echo; export MYSQL_PASSWORD; fi
"$MVN_BIN" -q -DskipTests package
exec java -jar target/mysql-meeting-lab-1.0.0.jar --spring.profiles.active="$PROFILE" "$@"
