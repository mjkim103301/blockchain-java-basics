#!/usr/bin/env bash
# contracts/*.sol 을 컴파일해 ABI 와 바이트코드를 뽑는다.
#
# 교재는 solcjs 를 쓴다.
#   $ npm install -g solc
#   $ solcjs --bin true --abi true -o out contracts/Coin.sol
#
# 여기서는 forge 를 쓴다. solc 를 알아서 받아 오고 버전이 고정돼 재현이 된다.
# 결과는 교재와 같은 .abi / .bin 두 파일로 떨어뜨린다.
set -euo pipefail
cd "$(dirname "$0")/.."

FORGE="${HOME}/.foundry/bin/forge"
command -v forge >/dev/null && FORGE=forge

"$FORGE" build

python3 - <<'PY'
import json, pathlib
out = pathlib.Path('src/main/resources/contracts'); out.mkdir(parents=True, exist_ok=True)

# contracts/*.sol 을 전부 훑는다. 파일 이름 = 컨트랙트 이름이라고 가정한다
# (이 프로젝트의 모든 .sol 이 그렇다). 새 컨트랙트를 추가해도 이 스크립트를
# 고칠 필요가 없다.
for sol in sorted(pathlib.Path('contracts').glob('*.sol')):
    name = sol.stem
    build_json = pathlib.Path(f'build/contracts/{name}.sol/{name}.json')
    if not build_json.exists():
        print(f'  (건너뜀) {name} — 빌드 산출물 없음'); continue
    j = json.loads(build_json.read_text())
    (out / f'{name}.abi').write_text(json.dumps(j['abi'], indent=2, ensure_ascii=False) + '\n')
    (out / f'{name}.bin').write_text(j['bytecode']['object'].removeprefix('0x') + '\n')
    print(f'  {name}.abi / {name}.bin  ({len(j["bytecode"]["object"])//2 - 1} bytes)')
PY
echo "완료 — src/main/resources/contracts/"
