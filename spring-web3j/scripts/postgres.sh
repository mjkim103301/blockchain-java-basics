#!/usr/bin/env bash
# 4주차부터 필요한 PostgreSQL 을 준비한다. docker 없이 brew 로 띄운다.
#
#   bash scripts/postgres.sh          최초 1회 — 설치·계정·DB 생성까지
#   bash scripts/postgres.sh start    이미 설치돼 있으면 그냥 기동
#
# 왜 docker-compose 가 아니라 brew 인가 — 이 레포의 다른 스크립트들도
# 전부 docker 없이 돈다(1주차 javac, 3주차 Remix, Anvil 도 바이너리 하나).
# 같은 결을 맞췄다. 팀원 환경에 docker 가 있다면 postgres:16 이미지를
# 띄우고 DB_URL/DB_USERNAME/DB_PASSWORD 환경변수만 바꿔 주면 그대로 된다.
set -euo pipefail

PSQL_BIN=/opt/homebrew/opt/postgresql@16/bin
export PATH="$PSQL_BIN:$PATH"

if [ "${1:-}" = "start" ]; then
  brew services start postgresql@16
  exit 0
fi

if ! command -v psql >/dev/null; then
  echo ">> PostgreSQL 16 설치 (brew)"
  brew install postgresql@16
fi

echo ">> 서비스 기동"
brew services start postgresql@16
sleep 2

OS_USER="$(whoami)"

echo ">> 애플리케이션 기본값과 맞춘 postgres/postgres 계정 생성 (이미 있으면 건너뜀)"
psql -U "$OS_USER" -d postgres -tc "SELECT 1 FROM pg_roles WHERE rolname='postgres'" | grep -q 1 || \
  psql -U "$OS_USER" -d postgres -c "CREATE USER postgres WITH SUPERUSER PASSWORD 'postgres';"

echo ">> blockchain_study DB 생성 (이미 있으면 건너뜀)"
psql -U "$OS_USER" -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='blockchain_study'" | grep -q 1 || \
  psql -U "$OS_USER" -d postgres -c "CREATE DATABASE blockchain_study OWNER postgres;"

echo ">> 접속 확인"
PGPASSWORD=postgres psql -h 127.0.0.1 -U postgres -d blockchain_study -c "select current_user, current_database();"

echo
echo "완료. application.yml 기본값(postgres/postgres, blockchain_study) 그대로 쓰면 된다."
