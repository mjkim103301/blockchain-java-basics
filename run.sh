#!/usr/bin/env bash
# 블록체인 기초 실습 — 컴파일 후 실행
#   bash run.sh
set -euo pipefail

cd "$(dirname "$0")"

# JDK 21 (Homebrew keg-only 라 PATH 에 안 잡힌다)
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21 ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

mkdir -p out
"$JAVAC" -encoding UTF-8 -d out src/*.java
"$JAVA" -Dfile.encoding=UTF-8 -cp out Main
