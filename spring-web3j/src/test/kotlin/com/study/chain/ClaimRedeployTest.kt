package com.study.chain

import com.study.chain.service.ClaimContractService
import com.study.chain.service.ValueTransferService
import com.study.chain.service.WalletService
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
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
 * 4주차 실습 "3. 3주차 컨트랙트를 로컬 블록체인에 배포" 를 검증한다.
 *
 * 3주차에서는 Remix VM 에서 손으로 Deploy 버튼을 누르고, pay/paidOf 버튼을
 * 눌러 가며 확인했다. 여기서는 같은 Claim.sol 을 Web3j 로 똑같이 배포하고
 * 호출한다 — 사람이 손으로 하던 걸 코드가 한다는 점만 다르고 체인 입장
 * 에서는 완전히 같은 트랜잭션이다.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("3주차 컨트랙트(Claim.sol) 재배포 — Remix로 하던 걸 Web3j로")
class ClaimRedeployTest {

    @Autowired private lateinit var web3j: Web3j
    @Autowired private lateinit var wallet: WalletService
    @Autowired private lateinit var transfer: ValueTransferService
    @Autowired private lateinit var claim: ClaimContractService
    @Autowired private lateinit var deployer: Credentials

    @BeforeAll
    fun chainMustBeUp() {
        val up = runCatching { web3j.ethBlockNumber().send().blockNumber }.isSuccess
        assumeTrue(up, "로컬 체인이 없어 건너뜀 — bash scripts/anvil.sh 로 띄우세요")
    }

    @Test
    @DisplayName("배포하면 insurer == 배포자 (3주차와 동일)")
    fun deployAndCheckInsurer() {
        val contract = claim.deploy(deployer)
        println("  Claim 배포 주소 : $contract")

        assertThat(claim.insurer(contract)).isEqualToIgnoringCase(deployer.address)
    }

    @Test
    @DisplayName("insurer 가 pay 하면 성공, paidOf 로 조회된다")
    fun insurerCanPay() {
        val contract = claim.deploy(deployer)
        val alice = wallet.create()

        val receipt = claim.pay(contract, deployer, alice.address, BigInteger.valueOf(100))
        assertThat(transfer.isSuccess(receipt)).isTrue()
        assertThat(claim.paidOf(contract, alice.address)).isEqualTo(BigInteger.valueOf(100))

        println("  pay gasUsed=${receipt.gasUsed}  paidOf(alice)=${claim.paidOf(contract, alice.address)}")
    }

    @Test
    @DisplayName("insurer 가 아닌 계정이 pay 하면 — 3주차와 같은 revert 메시지")
    fun strangerCannotPay() {
        val contract = claim.deploy(deployer)
        val stranger = wallet.create()
        transfer.signAndSend(deployer, stranger.address, "1")   // 가스비 지급

        val receipt = claim.pay(contract, stranger, stranger.address, BigInteger.ONE)

        assertThat(transfer.isSuccess(receipt)).isFalse()
        assertThat(receipt.status).isEqualTo("0x0")
        println("  revert. status=${receipt.status} — 3주차 Remix 에서 본 것과 같은 'not insurer'")
    }
}
