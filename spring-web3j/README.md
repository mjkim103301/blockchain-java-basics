# 블록체인 스터디 4주차 — Spring Boot × Web3j

이 프로젝트는 두 실습을 담는다.

1. 교재 **2강 「Understanding Account-based Blockchain」** 의 caver-js 예제를
   **Spring Boot + Kotlin + Web3j** 로 옮긴 것 (§1~7)
2. 공동 PoC **「전자계약 문서 무결성 검증 API」** — A(Solidity)·B(Spring+Web3j)·
   C(PostgreSQL) 세 역할이 만나는 지점 (§8~)

```bash
bash scripts/anvil.sh       # 1) 로컬 체인 (별도 터미널)
bash scripts/postgres.sh    # 2) PostgreSQL — 최초 1회, 이후엔 start 만
./gradlew test              # 3) 두 실습 전체를 통과시키는 테스트 (16개)
./gradlew bootRun           # 4) REST 로 하나씩 눌러보기
```

JDK 21만 있으면 된다.

> ⚠ **3주차까지와 달리, 이제는 Anvil 과 PostgreSQL 이 "둘 다" 떠 있어야 한다.**
> JPA(Spring Data)가 애플리케이션 시작 시점에 DataSource 를 필요로 하기 때문에,
> DB 가 꺼져 있으면 체인과 무관한 테스트까지 포함해 **컨텍스트 로딩 자체가 실패**한다
> (Anvil 만 없을 때처럼 "그 테스트만 조용히 skip" 되지 않는다 — 3초 안에 실패하도록
> `hikari.connection-timeout` 을 낮춰 뒀다). §9 에서 더 다룬다.

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
  Claim.sol             3주차 컨트랙트 원본 (로직 변경 없음)
  DocumentRegistry.sol  8절 — 문서 해시 등록·검증 (A 담당)

scripts/
  anvil.sh              로컬 체인. 인자로 block-time 지정 가능
  postgres.sh           PostgreSQL 설치·계정·DB 생성 (C 담당 인프라)
  compile.sh            forge build → 모든 .sol 의 .abi / .bin 자동 추출

src/main/kotlin/com/study/chain/
  config/Web3jConfig.kt             교재의 new Caver(url) 에 대응
  service/BlockchainStateService    1부 Blockchain State
  service/WalletService             SDK - accounts.wallet
  service/ValueTransferService      2부 Transactions
  service/ContractCodec             Bytecode & ABI (Coin·Claim·DocumentRegistry 전부)
  service/CoinContractService       3·4부 Smart Contracts + SDK
  service/ClaimContractService      8.2절 — 3주차 컨트랙트 재배포 (B 담당)
  service/DocumentHasher            SHA-256 — 1주차 Hashes 와 같은 원리
  service/DocumentRegistryService   8절 핵심 — register/verify (B 담당)
  service/DocumentLedger            PostgreSQL 쓰기 (C 담당, repo 에 위임)
  web/StudyController               1~7절 curl 로 눌러보는 용도
  web/DocumentController            8절 — 문서 무결성 API (B 담당)

src/main/kotlin/com/study/chain/repo/   8.5절 — PostgreSQL 엔티티 (C 담당)
  DocumentRecordEntity.kt
  DocumentRecordRepository.kt

src/main/resources/contracts/     컴파일 산출물 (.abi / .bin, 4개 전부)
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

---

# 8. 전자계약 문서 무결성 검증 API

공동 PoC 실습. **완료 기준**: 문서 해시 등록과 조회, 변경 여부 검증이 API 호출로 정상 수행된다 — curl 세 번으로 아래에서 그대로 재현했다.

```bash
curl -X POST localhost:8080/api/documents/deploy
# { "contractAddress": "0x1291be…" }

curl -X POST localhost:8080/api/documents/register -F "file=@contract.txt"
# { "docHash":"0xad7a25…", "status":"CONFIRMED", "txHash":"0xe3ba9b…", "blockNumber":60, "gasUsed":90258 }

curl -X POST localhost:8080/api/documents/verify -F "file=@contract.txt"
# { "match": true,  "verdict": "원본과 일치 — 변조되지 않았다" }

curl -X POST localhost:8080/api/documents/verify -F "file=@contract_수정됨.txt"
# { "match": false, "verdict": "불일치 — 변조되었거나 등록된 적 없는 내용" }
```

## 8.1 역할 분담 ↔ 코드

| 담당 | 개발 범위 | 코드 |
| --- | --- | --- |
| **A** | Solidity 컨트랙트 및 배포 | `contracts/DocumentRegistry.sol` |
| **B** | Spring Boot · Web3j API | `service/DocumentRegistryService.kt`, `web/DocumentController.kt` |
| **C** | PostgreSQL · 트랜잭션 상태 관리 | `repo/DocumentRecordEntity.kt`, `repo/DocumentRecordRepository.kt`, `service/DocumentLedger.kt` |

세 역할이 실제로 만나는 지점은 `DocumentRegistryService.register()` 한 곳이다 — A 가 정의한 `register(bytes32)` 를 B 가 Web3j 로 호출하고, 그 결과(txHash·status·gasUsed)를 C 의 테이블에 쓴다.

## 8.2 왜 3주차 Claim.sol 을 그대로 못 쓰는가

실습 순서에 **"3. 3주차 컨트랙트를 로컬 블록체인에 배포"** 가 있다. 그런데 `Claim.sol`은 "주소 → 지급액" 장부라서 **문서를 저장할 자리가 없다.**

그래서 두 가지를 분리해서 수행했다.

- **문자 그대로의 3단계** — `contracts/Claim.sol` (3주차 원본, 로직 변경 없음)을 Web3j로 Anvil에 배포한다. `ClaimContractService` + `ClaimRedeployTest`. 3주차에서 Remix로 손으로 누르던 걸 코드로 재현한 것뿐이다.
- **실제 문서 무결성 기능** — 해시만 저장하는 `contracts/DocumentRegistry.sol` 을 **새로 작성**했다. `require`·`mapping`·`event` 패턴은 Claim.sol과 동일하되, 저장하는 게 "지급액"이 아니라 "32바이트 지문"이라는 점만 다르다.

```solidity
mapping(bytes32 => Record) private records;   // 문서 내용이 아니라 문서의 해시가 key

function register(bytes32 docHash) external {
    require(records[docHash].registeredAt == 0, "Document already registered.");
    records[docHash] = Record(msg.sender, block.timestamp, block.number);
    emit DocumentRegistered(docHash, msg.sender, block.timestamp);
}
```

## 8.3 검증 원리 — 앵커링 패턴 그대로

**원본 파일은 어디에도 저장하지 않는다.** SHA-256 해시 32바이트만 체인에 올린다(1주차 `Hashes.sha256`과 같은 원리, `DocumentHasher.kt`). bytes32 타입을 고른 이유도 이것이다 — SHA-256 결과가 정확히 32바이트라서 변환 없이 그대로 들어맞는다.

```
등록할 때   파일 → SHA-256 → register(해시)         ← 파일 내용 자체는 전송만 되고 저장 안 됨
검증할 때   파일 → SHA-256 → verify(해시) → 등록 여부 확인
```

한 글자라도 바뀌면 SHA-256 결과가 완전히 달라진다(눈사태 효과, 1주차). 그래서 "등록된 해시와 일치하는가"만 보면 변조 여부가 그대로 드러난다. `exists=false` 는 "변조됐다"와 "애초에 등록 안 됐다"를 구분하지 않는다 — 어느 쪽이든 "이 내용은 체인이 보증하지 않는다"는 결론은 같기 때문이다.

## 8.4 실측 — 등록 → 조회 → 변조 검증

실제로 curl 로 돌려서 나온 값이다.

```
POST /documents/deploy
  contractAddress = 0x1291be112d480055dafd8a610b7d1e203891c274

POST /documents/register  (원본: "계약 당사자: OO생명 / 홍길동. 보험금: 1,000,000원...")
  docHash    = 0xad7a2533…d649589
  status     = CONFIRMED
  gasUsed    = 90,258          ← Claim.pay(46,370)보다 두 배 — 구조체 3칸을 한 번에 쓰기 때문
  blockNumber= 60

POST /documents/verify  (같은 파일)
  match = true   "원본과 일치 — 변조되지 않았다"

POST /documents/verify  (금액을 9,999,999원으로 바꾼 파일)
  docHash = 0xc651c8b2…(완전히 다른 값)
  match   = false  "불일치 — 변조되었거나 등록된 적 없는 내용"
```

PostgreSQL 에도 그대로 쌓인다.

```sql
select doc_hash, status, block_number, gas_used from document_record;

doc_hash                                                            | status    | block_number | gas_used
0xad7a2533…d649589                                                  | CONFIRMED |            60 |    90258
0xb4278f33…9b47cae3 (중복 등록 시도)                                  | REVERTED  |          null |    24084
```

**테스트 6개**가 이 흐름 전체를 자동으로 검증한다 (`DocumentIntegrityTest`).

```
5+6. 문서를 등록하면 CONFIRMED로 저장되고 txHash·블록번호가 남는다      PASSED
     같은 문서를 두 번 등록하면 두 번째는 REVERTED — 이미 등록됨       PASSED
7-가. 원본 그대로 검증하면 일치한다                                   PASSED
7-나. 원본이 한 글자라도 바뀌면 검증 실패 — 눈사태 효과                 PASSED
     등록한 적 없는 문서는 애초에 존재하지 않는다                      PASSED
     문서가 다른 계약서는 등록자(registrant)가 다를 수 있다            PASSED
```

## 8.5 PostgreSQL 쪽 설계 (담당 C)

```
document_record
  doc_hash          varchar(66)  PK   "0x" + SHA-256 64자리
  contract_address  varchar(42)
  tx_hash           varchar(66)       영수증이 아니라 접수증 — 아래 §8.6 참고
  registrant        varchar(42)
  status            varchar(16)       SUBMITTED / CONFIRMED / REVERTED / UNKNOWN
  block_number      bigint
  gas_used          bigint
  revert_reason     varchar(255)
  created_at / updated_at
```

**이 테이블에 들어가는 건 "체인 데이터"가 아니다.** "우리가 체인에 무엇을 보냈고 결과가 어떻게 됐는지"에 대한 우리 쪽 기록이다. 진실의 원천은 여전히 체인이고, 이 테이블이 체인과 어긋나면 체인이 맞다 — 빠른 조회와 장애 복구를 위한 사본일 뿐이다.

PoC 단계라 `ddl-auto: update` 로 스키마를 엔티티에 맞췄다. 운영이라면 Flyway/Liquibase 로 바꿔야 한다 — 여러 인스턴스가 동시에 `ddl-auto` 를 돌리면 위험하다(3주차 "배포는 수정이 아니다"와 같은 종류의 함정이다).

## 8.6 DB 쓰기와 체인 쓰기는 절대 한 트랜잭션이 아니다

```kotlin
val txHash = tx.sendRaw(signedHex)

// ⚠ 영수증을 기다리기 '전에' 저장한다 — 이 서비스에서 제일 중요한 한 줄
ledger.save(docHash, contract, txHash, from.address, SUBMITTED)

val receipt = tx.awaitReceipt(txHash)   // 여기서 죽어도 위 줄은 이미 커밋돼 있다
```

DB 트랜잭션(`@Transactional`)은 롤백이 된다. 체인 트랜잭션은 한 번 나가면 롤백이 없다. **둘을 하나의 `@Transactional` 로 묶으면 안 된다** — `DocumentLedger.save()`가 먼저 커밋되고, `awaitReceipt()`가 그 다음에 일어나는 구조를 유지해야 한다. 이게 지켜지면 서버가 영수증을 기다리다 죽어도 "제출은 했다"는 기록이 남아서 나중에 복구할 수 있다.

---

# 9. Web3j 빈과 JPA 빈이 충돌했던 일

실제로 겪은 버그라 적어 둔다. Postgres 를 붙이자마자 테스트가 전부 깨졌다.

```
BeanDefinitionOverrideException: Cannot register bean definition ... for bean
'transactionManager' since there is already [... Web3jConfig] bound.
```

`Web3jConfig` 에 있던 `fun transactionManager(...): TransactionManager` 가
**Spring 이 JPA 용으로 자동 등록하는 `PlatformTransactionManager` 빈과 이름이 겹쳤다.**
둘은 전혀 다른 타입(하나는 Web3j 의 "체인 트랜잭션" 매니저, 하나는 Spring 의
"DB 트랜잭션" 매니저)인데 Kotlin 함수 이름이 곧 빈 이름이 되는 바람에 충돌했다.

`web3jTransactionManager` 로 이름을 바꿔서 해결했다. 사소해 보이지만, **"트랜잭션"
이라는 같은 단어가 체인과 DB에서 완전히 다른 보장을 가리킨다**는 이 레포 전체의
주제를, 빈 이름 충돌이라는 형태로 실제로 겪은 셈이다.

---

## 10. 참고

- 교재 — 2강 Understanding Account-based Blockchain (2019)
- [Web3j 공식 문서](https://docs.web3j.io/) — core 4.14.0
- [Foundry · Anvil](https://getfoundry.sh/anvil/overview)
- [Ethereum JSON-RPC API](https://ethereum.org/ko/developers/docs/apis/json-rpc/)
- [Kaia 공식 문서](https://docs.kaia.io/) — Klaytn의 현재 이름
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/)
