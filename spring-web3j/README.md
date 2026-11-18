# 블록체인 스터디 4주차 — Spring Boot × Web3j

교재 **2강 「Understanding Account-based Blockchain」** 의 caver-js 예제를
**Spring Boot + Kotlin + Web3j** 로 그대로 옮긴 것이다.

```bash
bash scripts/anvil.sh      # 1) 로컬 체인 (별도 터미널)
./gradlew test             # 2) 교재 흐름 전체를 통과시키는 테스트
./gradlew bootRun          # 3) REST 로 하나씩 눌러보기
```

JDK 21만 있으면 된다. Anvil이 없으면 테스트는 **실패가 아니라 skip** 된다.

---

## ⚠ 교재와 달라진 점 — 먼저 읽을 것

교재는 **Klaytn / Baobab 테스트넷 / caver-js** 를 쓴다. 세 가지를 바꿨다.

| 교재 | 여기 | 왜 |
| --- | --- | --- |
| Klaytn Baobab 테스트넷 | **Anvil (로컬)** | Klaytn은 2024년 Finschia와 합병해 **Kaia**가 되었다. Baobab은 **Kairos**로 바뀌었고 교재의 `api.baobab.klaytn.net` 엔드포인트는 응답하지 않는다 |
| caver-js (Node.js) | **Web3j (Kotlin)** | 4주차 주제가 Spring Boot 연동이다. JSON-RPC 규격이 같아 코드가 1:1로 대응된다 |
| Solidity 0.5.6 | **0.8.24** | 0.5는 EOL이다. **컨트랙트 로직은 한 줄도 바꾸지 않았고**, 문법 차이는 `contracts/Coin.sol` 주석에 적어 두었다 |

> 교재의 개념(상태 기계 · TX · 가스 · 서명 · ABI · 컨트랙트)은 **플랫폼과 무관하게 그대로 통한다.**
> 이더리움과 Klaytn은 둘 다 account-based EVM 체인이고 JSON-RPC 인터페이스가 같다.

---

## 1. 교재 ↔ 이 코드 대응표

**이 표가 이 프로젝트의 핵심이다.** 교재를 펴놓고 옆에 두면 된다.

### 연결

| 교재 (caver-js) | 이 코드 (Web3j) |
| --- | --- |
| `new Caver('https://api.baobab...')` | `Web3j.build(HttpService(rpcUrl))` |
| — | `Credentials.create(privateKey)` |
| — | `RawTransactionManager(web3j, credentials, chainId)` |

→ `config/Web3jConfig.kt`

### 상태 읽기 — 전부 가스 0

| 교재 | 이 코드 |
| --- | --- |
| `caver.klay.getBlockNumber()` | `web3j.ethBlockNumber().send().blockNumber` |
| `caver.klay.getBalance(addr)` | `web3j.ethGetBalance(addr, LATEST).send().balance` |
| `caver.utils.toPeb('1','KLAY')` | `Convert.toWei("1", Unit.ETHER)` |

→ `service/BlockchainStateService.kt`

### 계정 / 지갑

| 교재 | 이 코드 |
| --- | --- |
| `caver.klay.accounts.create()` | `Credentials.create(Keys.createEcKeyPair())` |
| `wallet.add(account)` / `wallet[0]` / `wallet.length` | `WalletService.add() / at(0) / size()` |

→ `service/WalletService.kt`

> caver-js는 in-memory wallet을 SDK가 제공하지만 **Web3j에는 없다.** `Credentials`를 직접 들고 다니면 된다 — 사실 그게 전부다.

### 트랜잭션

| 교재 | 이 코드 |
| --- | --- |
| `accounts.signTransaction(tx, pk)` | `signOnly()` → `RawTransaction` + `TransactionEncoder` |
| `sendSignedTransaction(raw)` | `sendRaw()` → `web3j.ethSendRawTransaction` |
| `sendTransaction(tx)` (서명+전송) | `signAndSend()` |
| `.on('transactionHash', ...)` | `sendRaw()` 의 반환값 |
| `.on('receipt', ...)` | `awaitReceipt()` |

→ `service/ValueTransferService.kt`

> 교재의 `type: 'VALUE_TRANSFER'` 는 Klaytn 고유 필드다. 이더리움에는 없다 —
> **`to`에 주소가 있고 `data`가 비어 있으면 그게 곧 값 전송**이다.

### 컨트랙트

| 교재 | 이 코드 |
| --- | --- |
| `new caver.klay.Contract(abi)` + `.deploy({data}).send()` | `deploy()` — `to`를 비운 TX |
| `contract.methods.mint(a,100).send({...})` | `mint()` — **mutation** |
| `contract.methods.balances(a).call()` | `balanceOf()` — **constant** |
| `Coin.Sent().watch(...)` | `sentEventsOf(receipt)` |

→ `service/CoinContractService.kt`

---

## 2. ABI를 일부러 드러냈다

web3j CLI로 래퍼 클래스를 생성하는 방법도 있다.

```bash
web3j generate solidity -a Coin.abi -b Coin.bin -o src/main/java -p com.study.chain.contract
```

**여기서는 일부러 쓰지 않았다.** 래퍼를 쓰면 ABI 인코딩이 눈에 안 보이는데, 그게 이번 주차에 봐야 할 바로 그 부분이기 때문이다.

`service/ContractCodec.kt` 가 교재의 「Bytecode & ABI」 절을 코드로 만든 것이다.

```
mint(0xf39F…, 100)
  → 0x40c10f19                                                        ← 함수 선택자 4바이트
    000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266  ← 인자 1 (32바이트)
    0000000000000000000000000000000000000000000000000000000000000064  ← 인자 2 (32바이트)
```

선택자는 `keccak256("mint(address,uint256)")` 의 앞 4바이트다. **체인에는 함수 이름이 저장돼 있지 않고 이 4바이트로만 구분된다.** 테스트 4번이 이걸 검증한다.

실무에서는 생성된 래퍼를 쓰는 게 맞다. 개념이 잡힌 뒤에 바꾸면 된다.

---

## 3. 실행해 본 결과

### 테스트 7개 (실제 체인 대상)

```
1. 블록체인 상태를 읽는다 (가스 0)                      PASSED
2. 계정을 만든다 — 개인키는 그냥 난수다                   PASSED
3. 값 전송 — 서명과 전송은 별개 단계다                    PASSED
4. ABI 인코딩 — 함수 선택자 4바이트                      PASSED
5. 배포 → mint → send → 이벤트 (교재 Coin 컨트랙트)      PASSED
6. require 실패는 예외가 아니라 status=0x0 으로 온다      PASSED
7. 재배포는 수정이 아니라 새 컨트랙트 생성이다             PASSED
```

### 실측 가스

| 작업 | gasUsed | 설명 |
| --- | ---: | --- |
| 값 전송 | **21,000** | 실행되는 코드가 없다. 언제나 고정 |
| Coin 배포 | **267,176** | 바이트코드를 영구 저장한다 |
| `mint` | **46,370** | 빈 슬롯 최초 기록 20,000이 대부분 |
| `mint` revert | **24,156** | ⚠ **실패했는데 가스는 나갔다** |
| `balances` 조회 | **0** | `eth_call` — 트랜잭션이 아니다 |

### REST로 확인

```bash
curl localhost:8080/api/chain/state
{ "clientVersion":"anvil/v1.9.0", "chainId":31337, "blockNumber":11,
  "deployer":"0xf39f…2266", "balanceEther":9996.99, "nonce":9 }

curl -X POST localhost:8080/api/chain/coin/deploy
{ "contractAddress":"0x8a79…c318", "minter":"0xf39f…2266" }

curl -X POST "localhost:8080/api/chain/coin/0x8a79…/mint?to=0xf67b…&amount=100"
{ "txHash":"0xe327…78bb", "success":true, "gasUsed":46370, "balanceOfTo":100 }
```

---

## 4. 교재가 말하지 않는 것 — 실무에서 터지는 지점

교재는 SDK 사용법까지만 다룬다. **Spring Boot에 붙이는 순간 생기는 문제**가 따로 있다.

### ① revert는 예외로 오지 않는다 ← 가장 많이 놓친다

```kotlin
val receipt = coin.mint(contract, stranger, ...)   // require 가 막는 호출

receipt.status        // "0x0"   ← 실패인데
                      //            예외는 안 던져졌다
```

`require` 실패는 **HTTP 200 + 정상 영수증**으로 돌아온다. `try-catch`로는 절대 못 잡는다. **반드시 `receipt.status`를 확인해야 한다.** (테스트 6번)

### ② `.send()` 는 블로킹이다

Web3j가 내부에서 영수증을 polling한다. 로컬 Anvil은 즉시 끝나지만 **실제 네트워크에서는 12초 이상 스레드가 묶인다.** HTTP 요청 처리 중에 부르면 안 되고 워커로 빼야 한다.

> 이 프로젝트의 Controller는 **일부러 블로킹으로 두었다.** 교재 흐름을 눈으로 확인하는 게 목적이라서다. 실제 PoC에서는 Outbox 테이블 + 별도 워커 구조로 가야 한다.

### ③ 타임아웃이 나도 트랜잭션은 살아 있다

```kotlin
// awaitReceipt 가 던지는 메시지
"영수증 미도착 — 상태를 UNKNOWN 으로 두고 사람이 확인해야 한다"
```

**"실패했다"와 "응답을 못 받았다"를 구별할 수 없다.** 여기서 자동 재시도하면 이중 집행이 된다. 그래서 `FAILED`가 아니라 **`UNKNOWN`** 이라는 상태가 따로 필요하다.

### ④ nonce 충돌

`RawTransactionManager`는 매번 `eth_getTransactionCount`로 nonce를 읽는다. **인스턴스가 둘 이상이고 같은 지갑을 쓰면 같은 nonce를 받아 충돌한다.** 제출 워커는 단일 인스턴스로 돌려야 한다.

### ⑤ 개인키

`application.yml`에 개인키가 있는 건 **로컬 실습이라서**다. Anvil이 콘솔에 찍어주는 키는 **전 세계가 아는 공개된 값**이고, 메인넷에 쓰면 즉시 털린다. 운영에서는 HSM이나 KMS로 빼야 한다.

---

## 5. 프로젝트 구조

```
contracts/
  Coin.sol              교재 Coin 컨트랙트 (minter / mint / send / Sent)
  SimpleStorage.sol     교재 SimpleStorage (set=mutation, get=constant)

scripts/
  anvil.sh              로컬 체인. 인자로 block-time 지정 가능
  compile.sh            forge build → .abi / .bin 추출

src/main/kotlin/com/study/chain/
  config/Web3jConfig.kt           교재의 new Caver(url) 에 대응
  service/BlockchainStateService  1부 Blockchain State
  service/WalletService           SDK - accounts.wallet
  service/ValueTransferService    2부 Transactions
  service/ContractCodec           Bytecode & ABI
  service/CoinContractService     3·4부 Smart Contracts + SDK
  web/StudyController             curl 로 눌러보는 용도

src/main/resources/contracts/     컴파일 산출물 (.abi / .bin)
```

### 컨트랙트를 고쳤다면

```bash
bash scripts/compile.sh     # forge build → abi/bin 갱신
./gradlew test
```

`forge`가 없으면 교재 방식도 된다.

```bash
npm install -g solc
solcjs --bin true --abi true -o out contracts/Coin.sol
```

---

## 6. 테스트에서 꼭 바꿔볼 것

```bash
bash scripts/anvil.sh 12      # ← 12초마다 블록 생성
```

기본값(즉시 생성)으로만 돌리면 **트랜잭션이 pending에 머무는 상황을 한 번도 못 만난다.** 그런데 운영 사고는 정확히 거기서 난다.

`--block-time 12`로 띄우면 `awaitReceipt`가 실제로 기다리고, `receiptPollAttempts`를 넘기면 `UNKNOWN` 경로를 눈으로 볼 수 있다.

---

## 7. 참고

- 교재 — 2강 Understanding Account-based Blockchain (2019)
- [Web3j 공식 문서](https://docs.web3j.io/) — core 4.14.0
- [Foundry · Anvil](https://getfoundry.sh/anvil/overview)
- [Ethereum JSON-RPC API](https://ethereum.org/ko/developers/docs/apis/json-rpc/)
- [Kaia 공식 문서](https://docs.kaia.io/) — Klaytn의 현재 이름
