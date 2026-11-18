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
for name in ['Coin', 'SimpleStorage']:
    j = json.loads(pathlib.Path(f'build/contracts/{name}.sol/{name}.json').read_text())
    (out / f'{name}.abi').write_text(json.dumps(j['abi'], indent=2, ensure_ascii=False) + '\n')
    (out / f'{name}.bin').write_text(j['bytecode']['object'].removeprefix('0x') + '\n')
    print(f'  {name}.abi / {name}.bin  ({len(j["bytecode"]["object"])//2 - 1} bytes)')
PY
echo "완료 — src/main/resources/contracts/"
