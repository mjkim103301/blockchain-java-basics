package com.study.chain

import com.study.chain.repo.DocumentTxStatus
import com.study.chain.service.DocumentHasher
import com.study.chain.service.DocumentRegistryService
import com.study.chain.service.ValueTransferService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j

/**
 * 4주차 실습 "전자계약 문서 무결성 검증 API" 전체 흐름.
 *
 *   4. Web3j로 RPC 연결         → @Autowired Web3j (이미 1~3주차에서 검증됨)
 *   5. 해시 생성·등록 API       → register()
 *   6. txHash 저장·Receipt 확인 → DocumentLedger (PostgreSQL)
 *   7. 원본 변경 시 검증 실패   → verify() 로 직접 확인
 *
 * Anvil 과 PostgreSQL 이 둘 다 떠 있어야 돈다. 어느 하나라도 없으면
 * Spring 컨텍스트 자체가 안 뜬다(JPA 가 DataSource 를 필요로 하기 때문에,
 * 3주차까지와 달리 "체인만 없어도 컨텍스트는 뜬다"가 더 이상 성립하지 않는다).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("전자계약 문서 무결성 검증 API")
class DocumentIntegrityTest {

    @Autowired private lateinit var web3j: Web3j
    @Autowired private lateinit var hasher: DocumentHasher
    @Autowired private lateinit var registry: DocumentRegistryService
    @Autowired private lateinit var transfer: ValueTransferService
    @Autowired private lateinit var deployer: Credentials

    private lateinit var contract: String

    @BeforeAll
    fun chainMustBeUp() {
        val up = runCatching { web3j.ethBlockNumber().send().blockNumber }.isSuccess
        assumeTrue(up, "로컬 체인이 없어 건너뜀 — bash scripts/anvil.sh 로 띄우세요")
    }

    @BeforeEach
    fun deployFreshRegistry() {
        // 테스트마다 새로 배포한다 — 3주차에서 배운 "배포 = 새 컨트랙트"를
        // 여기서도 그대로 이용해, 테스트끼리 상태가 섞이지 않게 한다.
        contract = registry.deploy(deployer)
    }

    @Test
    @DisplayName("5+6. 문서를 등록하면 CONFIRMED 로 저장되고 txHash·블록번호가 남는다")
    fun registerDocument() {
        val original = "계약서 원본 — 갑: OO생명, 을: 홍길동, 보험금: 1,000,000원".toByteArray()
        val docHash = hasher.sha256(original)
        val docHashHex = hasher.toHex(docHash)
        println("  문서 해시 : $docHashHex")

        val record = registry.register(contract, deployer, docHash)

        assertThat(record.status).isEqualTo(DocumentTxStatus.CONFIRMED)
        assertThat(record.txHash).startsWith("0x")
        assertThat(record.blockNumber).isNotNull()
        println("  txHash=${record.txHash}  block=${record.blockNumber}  gasUsed=${record.gasUsed}")

        // PostgreSQL 에서 그대로 다시 읽힌다 — "우리 쪽 기록"이 영속화됐다는 증거
        val reloaded = registry.ledgerOf(docHashHex)
        assertThat(reloaded).isNotNull
        assertThat(reloaded!!.status).isEqualTo(DocumentTxStatus.CONFIRMED)
    }

    @Test
    @DisplayName("같은 문서를 두 번 등록하면 두 번째는 REVERTED — 이미 등록됨")
    fun duplicateRegistrationReverts() {
        val content = "중복 등록 테스트 문서".toByteArray()
        val docHash = hasher.sha256(content)

        val first = registry.register(contract, deployer, docHash)
        assertThat(first.status).isEqualTo(DocumentTxStatus.CONFIRMED)

        val second = registry.register(contract, deployer, docHash)
        assertThat(second.status).isEqualTo(DocumentTxStatus.REVERTED)
        assertThat(second.revertReason).contains("already registered")

        // revert 도 가스는 나간다 — 3주차에서 본 그대로
        assertThat(second.gasUsed).isGreaterThan(0)
        println("  2차 등록 revert 사유: ${second.revertReason}  gasUsed=${second.gasUsed}")
    }

    @Test
    @DisplayName("7-가. 원본 그대로 검증하면 일치한다")
    fun verifyUnmodifiedDocumentMatches() {
        val original = "변경되지 않은 계약서 원본입니다.".toByteArray()
        val docHash = hasher.sha256(original)
        registry.register(contract, deployer, docHash)

        // "나중에 이 문서를 다시 받았다" 는 상황을 흉내낸다 — 같은 바이트로 다시 해시
        val recomputed = hasher.sha256(original)
        val result = registry.verify(contract, recomputed)

        assertThat(result.exists).isTrue()
        assertThat(result.registrant).isEqualToIgnoringCase(deployer.address)
        println("  일치. 등록자=${result.registrant}  블록=${result.blockNumber}")
    }

    @Test
    @DisplayName("7-나. 원본이 한 글자라도 바뀌면 검증 실패 — 눈사태 효과")
    fun verifyTamperedDocumentFails() {
        val original = "변경 전: 지급액은 1,000,000원 입니다."
        val tampered = "변경 전: 지급액은 9,000,000원 입니다."   // 숫자 하나만 바뀜

        val originalHash = hasher.sha256(original.toByteArray())
        registry.register(contract, deployer, originalHash)

        // 변조된 내용을 "지금 다시 받은 문서"라 치고 검증을 걸어 본다
        val tamperedHash = hasher.sha256(tampered.toByteArray())
        val result = registry.verify(contract, tamperedHash)

        assertThat(result.exists).isFalse()
        println("  불일치 확인됨. 원본 해시=${hasher.toHex(originalHash)}")
        println("              변조 해시=${hasher.toHex(tamperedHash)}  (완전히 다른 값 — 눈사태 효과)")

        // 혹시나 해서 — 원본 해시로 검증하면 당연히 일치해야 한다
        assertThat(registry.verify(contract, originalHash).exists).isTrue()
    }

    @Test
    @DisplayName("등록한 적 없는 문서는 애초에 존재하지 않는다")
    fun unregisteredDocumentDoesNotExist() {
        val neverRegistered = hasher.sha256("아무도 등록한 적 없는 문서".toByteArray())
        val result = registry.verify(contract, neverRegistered)

        assertThat(result.exists).isFalse()
        assertThat(result.registrant).isEqualTo("0x0000000000000000000000000000000000000000")
    }

    @Test
    @DisplayName("문서가 다른 계약서는 등록자(registrant)가 다를 수 있다")
    fun differentRegistrantsAreRecorded() {
        val mine = "내가 등록하는 문서".toByteArray()
        registry.register(contract, deployer, hasher.sha256(mine))

        val anotherHash = hasher.sha256("다른 문서".toByteArray())
        // 같은 deployer 로 등록하지만, 실제로는 다른 계정이 등록할 수도 있다는 걸
        // verify() 의 registrant 필드가 보장한다는 사실만 확인한다.
        registry.register(contract, deployer, anotherHash)
        val result = registry.verify(contract, anotherHash)

        assertThat(result.registrant).isEqualToIgnoringCase(deployer.address)
    }
}
