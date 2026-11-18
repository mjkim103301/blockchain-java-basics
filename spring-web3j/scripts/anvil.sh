#!/usr/bin/env bash
# 로컬 테스트 체인을 띄운다. H2 인메모리 DB 를 띄우는 것과 같은 감각이다.
#
#   bash scripts/anvil.sh          블록 즉시 생성 (빠른 개발용)
#   bash scripts/anvil.sh 12       12초마다 블록 생성 (pending 상황 재현용)
#
# 두 번째 모드가 중요하다. 즉시 생성 모드로만 테스트하면
# "트랜잭션이 멤풀에 머무는" 상황을 한 번도 못 만나는데,
# 운영 사고는 정확히 거기서 난다.
set -euo pipefail

ANVIL="${HOME}/.foundry/bin/anvil"
command -v anvil >/dev/null && ANVIL=anvil

if [ ! -x "$ANVIL" ] && ! command -v anvil >/dev/null; then
  echo "anvil 이 없습니다. 아래로 설치하세요:"
  echo "  curl -L https://foundry.paradigm.xyz | bash && foundryup"
  exit 1
fi

if [ $# -ge 1 ]; then
  echo ">> ${1}초마다 블록 생성"
  exec "$ANVIL" --block-time "$1"
else
  echo ">> 트랜잭션이 오면 즉시 블록 생성"
  exec "$ANVIL"
fi
