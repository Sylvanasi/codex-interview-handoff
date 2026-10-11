#!/usr/bin/env bash
# 本机没装 JDK 时默认用 IntelliJ 自带的 JBR；有 JDK 就 export JAVA_HOME=... 覆盖。
set -euo pipefail

resolve_java_home() {
  local candidates=()
  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidates+=("$JAVA_HOME")
  fi
  if command -v javac >/dev/null 2>&1; then
    candidates+=("$(dirname "$(dirname "$(command -v javac)")")")
  fi
  candidates+=("/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home")

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate/bin/javac" ]] && "$candidate/bin/javac" -version >/dev/null 2>&1; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

if ! JAVA_HOME="$(resolve_java_home)"; then
  echo "没找到可用的 JDK：请安装 JDK 17+，或 export JAVA_HOME=/path/to/jdk" >&2
  exit 1
fi
echo "使用 JDK：$JAVA_HOME"

cd "$(dirname "$0")"
mkdir -p out
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d out src/*.java
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp out PushLab
