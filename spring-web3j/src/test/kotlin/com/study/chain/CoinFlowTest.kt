package com.study.chain

import com.study.chain.service.*
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j

/**
 * 교재 2강의 흐름을 그대로 한 번 통과시키는 통합 테스트.
 *
 *   상태 읽기 → 계정 → 값 전송 → 컨트랙트 배포 → mint → send → 이벤트
 *
 * 로컬 체인(Anvil)이 떠 있어야 돈다. 없으면 **실패가 아니라 skip** 된다.
 *   bash scripts/anvil.sh
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("교재 2강 전체 흐름")
class CoinFlowTest {

    @Autowired private lateinit var web3j: Web3j
    @Autowired private lateinit var state: BlockchainStateService
    @Autowired private lateinit var wallet: WalletService
    @Autowired private lateinit var transfer: ValueTransferService
    @Autowired private lateinit var coin: CoinContractService
    @Autowired private lateinit var codec: ContractCodec
    @Autowired private lateinit var deployer: Credentials

    @BeforeAll
    fun chainMustBeUp() {
        val up = runCatching { web3j.ethBlockNumber().send().blockNumber }.isSuccess
        assumeTrue(up, "로컬 체인이 없어 건너뜀 — bash scripts/anvil.sh 로 띄우세요")
    }

    @Test
    @DisplayName("1. 블록체인 상태를 읽는다 (가스 0)")
    fun readState() {
        println("  클라이언트 : ${state.clientVersion()}")
        println("  체인 ID    : ${state.chainId()}")
        println("  블록 번호  : ${state.blockNumber()}")
        println("  배포자     : ${deployer.address}")
        println("  잔액       : ${state.balanceEther(deployer.address)} ETH")

        assertThat(state.blockNumber()).isGreaterThanOrEqualTo(BigInteger.ZERO)
        assertThat(state.balanceEther(deployer.address).toDouble()).isGreaterThan(0.0)
    }

    @Test
    @DisplayName("2. 계정을 만든다 — 개인키는 그냥 난수다")
    fun createAccounts() {
        wallet.clear()
        val a = wallet.create()
        val b = wallet.create()

        println("  생성된 주소 : ${a.address}, ${b.address}")
        assertThat(wallet.size()).isEqualTo(2)
        assertThat(a.address).isNotEqualTo(b.address)
        assertThat(wallet.at(0)!!.address).isEqualTo(a.address)
    }

    @Test
    @DisplayName("3. 값 전송 — 서명과 전송은 별개 단계다")
    fun valueTransfer() {
        val receiver = wallet.create()

        // ① 서명만. 아직 네트워크에 안 나갔다
        val signed = transfer.signOnly(deployer, receiver.address, "1")
        println("  서명 결과 nonce=${signed.nonce} txHash=${signed.txHash.take(14)}…")
        assertThat(signed.rawTransaction).startsWith("0x")

        // ② 전송 → 접수증(해시)만 돌아온다
        val hash = transfer.sendRaw(signed.rawTransaction)
        assertThat(hash).isEqualTo(signed.txHash)

        // ③ 영수증을 기다려야 성공/실패를 안다
        val receipt = transfer.awaitReceipt(hash)
        assertThat(transfer.isSuccess(receipt)).isTrue()
        assertThat(receipt.gasUsed).isEqualTo(BigInteger.valueOf(21_000))   // 코드 실행 없음
        println("  gasUsed=${receipt.gasUsed}  (단순 전송은 언제나 21,000)")

        assertThat(state.balanceEther(receiver.address).toDouble()).isEqualTo(1.0)
    }

    @Test
    @DisplayName("4. ABI 인코딩 — 함수 선택자 4바이트")
    fun abiEncoding() {
        val data = codec.encodeMint("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", BigInteger.valueOf(100))
        println("  mint(addr,100) → $data")

        assertThat(data).startsWith("0x")
        // 선택자 4바이트 + 인자 32바이트 × 2 = 68바이트 = 136자 + "0x"
        assertThat(data.length).isEqualTo(2 + 8 + 64 + 64)
        assertThat(data.substring(2, 10)).isEqualTo("40c10f19")   // keccak256("mint(address,uint256)") 앞 4B
    }

    @Test
    @DisplayName("5. 배포 → mint → send → 이벤트 (교재 Coin 컨트랙트)")
    fun coinFullFlow() {
        val alice = wallet.create()
        val bob = wallet.create()

        // ───── 배포. to 가 비어 있는 TX 다
        val contract = coin.deploy(deployer)
        println("  배포 주소 : $contract")
        assertThat(coin.minter(contract)).isEqualToIgnoringCase(deployer.address)

        // ───── mint : 상태를 바꾸므로 TX
        val mintReceipt = coin.mint(contract, deployer, alice.address, BigInteger.valueOf(100))
        assertThat(transfer.isSuccess(mintReceipt)).isTrue()
        println("  mint gasUsed=${mintReceipt.gasUsed}")

        // ───── balances : 읽기만 하므로 eth_call. 가스 0
        assertThat(coin.balanceOf(contract, alice.address)).isEqualTo(BigInteger.valueOf(100))
        assertThat(coin.balanceOf(contract, bob.address)).isEqualTo(BigInteger.ZERO)

        // ───── send : alice 가 bob 에게. 가스비는 alice 가 내야 하므로 ETH 를 먼저 준다
        transfer.signAndSend(deployer, alice.address, "1")
        val sendReceipt = coin.send(contract, alice, bob.address, BigInteger.valueOf(30))
        assertThat(transfer.isSuccess(sendReceipt)).isTrue()

        assertThat(coin.balanceOf(contract, alice.address)).isEqualTo(BigInteger.valueOf(70))
        assertThat(coin.balanceOf(contract, bob.address)).isEqualTo(BigInteger.valueOf(30))

        // ───── 이벤트 : indexed 는 topics, 나머지는 data
        val events = coin.sentEventsOf(sendReceipt)
        assertThat(events).hasSize(1)
        with(events[0]) {
            assertThat(from).isEqualToIgnoringCase(alice.address)
            assertThat(to).isEqualToIgnoringCase(bob.address)
            assertThat(amount).isEqualTo(BigInteger.valueOf(30))
            println("  Sent 이벤트 : ${from.take(10)}… → ${to.take(10)}… $amount")
        }
    }

    @Test
    @DisplayName("6. require 실패는 예외가 아니라 status=0x0 으로 온다")
    fun revertIsNotAnException() {
        val contract = coin.deploy(deployer)
        val stranger = wallet.create()
        transfer.signAndSend(deployer, stranger.address, "1")   // 가스비

        // minter 가 아닌 계정이 mint → require 가 막는다
        val receipt = coin.mint(contract, stranger, stranger.address, BigInteger.valueOf(1))

        // ⚠ 여기가 핵심 — 예외가 던져지지 않았다
        assertThat(transfer.isSuccess(receipt)).isFalse()
        assertThat(receipt.status).isEqualTo("0x0")
        println("  revert 인데 예외 없음. status=${receipt.status} gasUsed=${receipt.gasUsed}")

        // 상태는 그대로 (되돌아갔다)
        assertThat(coin.balanceOf(contract, stranger.address)).isEqualTo(BigInteger.ZERO)

        // requireSuccess 를 써야 비로소 예외가 된다
        assertThatThrownBy { transfer.requireSuccess(receipt) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("revert")
    }

    @Test
    @DisplayName("7. 재배포는 수정이 아니라 새 컨트랙트 생성이다")
    fun redeployCreatesNewContract() {
        val first = coin.deploy(deployer)
        coin.mint(first, deployer, deployer.address, BigInteger.valueOf(50))

        val second = coin.deploy(deployer)

        assertThat(second).isNotEqualToIgnoringCase(first)
        assertThat(coin.balanceOf(first, deployer.address)).isEqualTo(BigInteger.valueOf(50))
        assertThat(coin.balanceOf(second, deployer.address)).isEqualTo(BigInteger.ZERO)
        println("  1차 $first → 50")
        println("  2차 $second → 0  (별개 컨트랙트)")
    }
}
