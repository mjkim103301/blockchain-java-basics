package com.study.chain.web

import com.study.chain.repo.DocumentRecordEntity
import com.study.chain.service.DocumentHasher
import com.study.chain.service.DocumentRegistryService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.web3j.crypto.Credentials

/**
 * 4주차 실습 "전자계약 문서 무결성 검증 API".
 *
 * 역할 분담
 *   A  contracts/DocumentRegistry.sol        — 온체인 저장 규칙
 *   B  이 컨트롤러 + service/Document*        — Web3j 연동, ABI 인코딩
 *   C  repo/DocumentRecordEntity + PostgreSQL — 트랜잭션 상태 영속화
 *
 * 세 역할이 만나는 지점이 DocumentRegistryService 다 — A 가 정의한
 * register/verify 를 B 가 Web3j 로 호출하고, 그 결과를 C 의 테이블에 쓴다.
 *
 * 완료 기준 재확인 — curl 세 번으로 전부 확인된다.
 *   1) POST /register  — 문서 등록
 *   2) GET  /verify     — 등록된 그대로의 내용으로 조회 → 일치
 *   3) GET  /verify     — 한 글자 바꾼 내용으로 조회 → 불일치 (변조 탐지)
 */
@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val hasher: DocumentHasher,
    private val registry: DocumentRegistryService,
    private val deployer: Credentials,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 데모용 — 실제로는 컨트랙트 주소를 설정이나 DB에 고정해 둔다. */
    @Volatile
    private var contractAddress: String? = null

    // ------------------------------------------------------------ 배포

    @PostMapping("/deploy")
    fun deploy(): Map<String, Any> {
        val address = registry.deploy(deployer)
        contractAddress = address
        return mapOf("contractAddress" to address)
    }

    private fun requireContract(): String =
        contractAddress ?: error("아직 DocumentRegistry 가 배포되지 않았다. POST /api/documents/deploy 를 먼저 호출할 것.")

    // --------------------------------------------------------- 5. 등록

    /**
     * 문서를 해시해서 체인에 등록한다.
     *
     * multipart 파일로 받는다 — 계약서 PDF 같은 실제 사용 시나리오를
     * 그대로 흉내 내기 위해서다. **원본 파일 내용은 어디에도 저장하지
     * 않는다.** 해시만 계산해서 넘긴다.
     */
    @PostMapping("/register", consumes = ["multipart/form-data"])
    fun register(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, Any?>> {
        val docHash = hasher.sha256(file.bytes)
        val docHashHex = hasher.toHex(docHash)

        val record = try {
            registry.register(requireContract(), deployer, docHash)
        } catch (e: IllegalStateException) {
            log.error("문서 등록 실패 docHash={}", docHashHex, e)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("docHash" to docHashHex, "error" to e.message))
        }

        val status = if (record.status.name == "CONFIRMED") HttpStatus.CREATED else HttpStatus.CONFLICT
        return ResponseEntity.status(status).body(record.toResponse())
    }

    /** 이미 계산해 둔 해시를 직접 등록하고 싶을 때 쓰는 경로. 테스트에서 주로 쓴다. */
    @PostMapping("/register/hash")
    fun registerHash(@RequestParam docHash: String): Map<String, Any?> {
        val bytes = hasher.fromHex(docHash)
        return registry.register(requireContract(), deployer, bytes).toResponse()
    }

    // ------------------------------------------------------ 6. 상태 조회

    /** PostgreSQL 에 쌓인 "우리 쪽 기록"을 조회한다. 체인을 매번 다시 묻지 않는다. */
    @GetMapping("/{docHash}")
    fun getRecord(@PathVariable docHash: String): ResponseEntity<Map<String, Any?>> {
        val record = registry.ledgerOf(docHash) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(record.toResponse())
    }

    // --------------------------------------------------------- 7. 검증

    /**
     * 손에 든 문서가 등록 당시와 같은지 확인한다.
     *
     * 원리는 간단하다 — **지금 받은 파일을 다시 해시해서, 그 해시가
     * 체인에 등록돼 있는지만 본다.** 원본과 한 바이트라도 다르면
     * 해시가 완전히 달라지므로(눈사태 효과, 1주차) exists=false 가 나온다.
     * "등록된 적 없음"과 "변조됨"을 이 API 선에서는 구분하지 않는다 —
     * 어느 쪽이든 "지금 이 내용은 체인이 보증하지 않는다"는 결론은 같다.
     */
    @PostMapping("/verify", consumes = ["multipart/form-data"])
    fun verify(@RequestParam("file") file: MultipartFile): Map<String, Any?> {
        val docHash = hasher.sha256(file.bytes)
        val docHashHex = hasher.toHex(docHash)
        val onChain = registry.verify(requireContract(), docHash)

        return mapOf(
            "docHash" to docHashHex,
            "match" to onChain.exists,
            "verdict" to if (onChain.exists) "원본과 일치 — 변조되지 않았다" else "불일치 — 변조되었거나 등록된 적 없는 내용",
            "registrant" to onChain.registrant.takeIf { onChain.exists },
            "registeredAt" to onChain.registeredAt.takeIf { onChain.exists },
            "blockNumber" to onChain.blockNumber.takeIf { onChain.exists },
        )
    }

    /** 해시를 이미 알고 있을 때 쓰는 조회 전용 경로 (가스 0 — eth_call). */
    @GetMapping("/verify")
    fun verifyByHash(@RequestParam docHash: String): Map<String, Any?> {
        val bytes = hasher.fromHex(docHash)
        val onChain = registry.verify(requireContract(), bytes)
        return mapOf(
            "docHash" to docHash,
            "exists" to onChain.exists,
            "registrant" to onChain.registrant,
            "registeredAt" to onChain.registeredAt,
            "blockNumber" to onChain.blockNumber,
        )
    }

    private fun DocumentRecordEntity.toResponse(): Map<String, Any?> = mapOf(
        "docHash" to docHash,
        "contractAddress" to contractAddress,
        "txHash" to txHash,
        "registrant" to registrant,
        "status" to status.name,
        "blockNumber" to blockNumber,
        "gasUsed" to gasUsed,
        "revertReason" to revertReason,
    )
}
