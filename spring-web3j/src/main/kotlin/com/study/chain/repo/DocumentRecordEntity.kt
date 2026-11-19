package com.study.chain.repo

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 담당 C(PostgreSQL · 트랜잭션 상태 관리) 의 테이블.
 *
 * ⚠ 이 테이블에 들어가는 건 "체인 데이터"가 아니라 "우리가 체인에
 * 무엇을 보냈고 그 결과가 어떻게 됐는지에 대한 우리 쪽 기록"이다.
 * 진실의 원천(source of truth)은 여전히 체인이다 — 이 테이블이
 * 체인과 어긋나면 체인이 맞다. 이 테이블은 "빠르게 조회하기 위한 사본"
 * 이자 "장애 복구용 단서"일 뿐이다.
 *
 * docHash 를 기본키로 쓰는 이유 — DocumentRegistry.sol 자체가
 * "같은 해시는 한 번만 등록 가능"을 강제하므로, 우리 쪽 테이블도
 * 같은 제약을 걸어 두면 체인과 DB가 같은 규칙을 공유하게 된다.
 */
@Entity
@Table(name = "document_record")
class DocumentRecordEntity(

    /** "0x" + 64자리 16진수(SHA-256). 문서 내용이 아니라 지문만 들어간다. */
    @Id
    @Column(name = "doc_hash", length = 66, nullable = false)
    val docHash: String,

    @Column(name = "contract_address", length = 42, nullable = false)
    var contractAddress: String,

    /** 영수증이 아니라 접수증. awaitReceipt 보다 먼저 저장되는 값. */
    @Column(name = "tx_hash", length = 66, nullable = false)
    var txHash: String,

    @Column(name = "registrant", length = 42, nullable = false)
    var registrant: String,

    /** SUBMITTED → CONFIRMED/REVERTED, 혹은 → UNKNOWN. 상태 머신은 DocumentTxStatus 참고. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    var status: DocumentTxStatus,

    @Column(name = "block_number")
    var blockNumber: Long? = null,

    @Column(name = "gas_used")
    var gasUsed: Long? = null,

    /** register() 가 revert 됐을 때의 사유. 보통 "Document already registered." */
    @Column(name = "revert_reason", length = 255)
    var revertReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    /** JPA/Hibernate 가 프록시를 만들 때 쓰는 생성자. kotlin-jpa 플러그인이 자동으로 채워 준다. */
    protected constructor() : this(
        docHash = "",
        contractAddress = "",
        txHash = "",
        registrant = "",
        status = DocumentTxStatus.UNKNOWN,
    )
}

/**
 * 「Spring Boot와 체인 잇기」에서 다룬 상태 머신의 축소판.
 *
 *   SUBMITTED  해시만 받고 영수증 대기 중 (멤풀에 있을 수 있음)
 *   CONFIRMED  영수증 status=0x1 — 체인에 실제로 등록됨
 *   REVERTED   영수증 status=0x0 — 보통 "이미 등록된 문서"
 *   UNKNOWN    영수증 polling 타임아웃. 제출 여부조차 불확실.
 *              ⚠ 이 상태는 자동 재시도하면 안 된다.
 */
enum class DocumentTxStatus { SUBMITTED, CONFIRMED, REVERTED, UNKNOWN }
