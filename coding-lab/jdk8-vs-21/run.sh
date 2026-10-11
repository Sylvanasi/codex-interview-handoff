#!/usr/bin/env bash
# JDK 8 → 17/21 对照实验。需要 JDK 21+（用 25 也能跑）。
set -euo pipefail

resolve_java_home() {
  local candidates=()
  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidates+=("$JAVA_HOME")
  fi
  if command -v javac >/dev/null 2>&1; then
    candidates+=("$(dirname "$(dirname "$(command -v javac)")")")
  fi
  # 本机可能装的 JDK 21 / 25
  if [[ -d "$HOME/Library/Java/JavaVirtualMachines" ]]; then
    for d in "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home; do
      [[ -d "$d" ]] && candidates+=("$d")
    done
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
  echo "没找到可用的 JDK：请装 JDK 21+，或 export JAVA_HOME=/path/to/jdk21" >&2
  exit 1
fi

# 版本检查：本工程用了 JDK 21 才有的语法，JDK 8 一定编译不过
VERSION="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "$VERSION" -lt 21 ]]; then
  echo "当前 JDK 是 ${VERSION}，本工程需要 21+。JDK 8 编译不过去，这是正常的。" >&2
  echo "两条路：export JAVA_HOME 指向 JDK 21，或直接用 IntelliJ 自带的 JBR 25。" >&2
  exit 1
fi
echo "使用 JDK：${JAVA_HOME}（版本 ${VERSION}）"

cd "$(dirname "$0")"
mkdir -p out
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 21 -d out src/*.java
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp out JdkLab
