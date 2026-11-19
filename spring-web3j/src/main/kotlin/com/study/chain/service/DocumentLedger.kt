package com.study.chain.service

import com.study.chain.repo.DocumentRecordEntity
import com.study.chain.repo.DocumentRecordRepository
import com.study.chain.repo.DocumentTxStatus
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 4주차 실습 "6. 트랜잭션 해시 저장 및 Receipt 확인" 을 구현하는 부분.
 * PostgreSQL(담당 C) 위에 올라간다 — DocumentRecordRepository 가
 * Spring Data JPA 로 실제 INSERT/UPDATE 를 만들어 낸다.
 *
 * 여기서 저장하는 건 "DB 트랜잭션"이 아니라 "블록체인 트랜잭션"의 기록이다.
 * 이름이 같아서 헷갈리기 쉬운데 완전히 다른 두 개념이다 — DB 쪽은
 * @Transactional 로 롤백이 되지만, 체인 쪽은 한 번 나가면 롤백이 없다.
 * 그래서 이 클래스의 메서드 각각은 "DB 트랜잭션 하나"이고, DocumentRegistryService
 * 가 그 사이사이에 "체인 트랜잭션"을 끼워 넣는 구조다. 이 둘을 하나의
 * @Transactional 로 묶으면 안 된다 — 「Spring Boot와 체인 잇기」의
 * 핵심 교훈이 바로 이것이다.
 */
@Service
class DocumentLedger(private val repo: DocumentRecordRepository) {

    @Transactional
    fun save(
        docHash: String,
        contractAddress: String,
        txHash: String,
        registrant: String,
        status: DocumentTxStatus,
    ): DocumentRecordEntity =
        repo.save(
            DocumentRecordEntity(
                docHash = docHash.lowercase(),
                contractAddress = contractAddress,
                txHash = txHash,
                registrant = registrant,
                status = status,
            )
        )

    /**
     * 영수증을 받은 뒤 상태를 갱신한다.
     *
     * ⚠ docHash 로 먼저 조회해서 "없으면 예외"를 던진다 — save() 를
     * 건너뛰고 update() 부터 부르는 실수를 조용히 넘기지 않기 위해서다.
     */
    @Transactional
    fun update(docHash: String, transform: (DocumentRecordEntity) -> Unit): DocumentRecordEntity {
        val entity = repo.findById(docHash.lowercase()).orElseThrow {
            IllegalStateException("ledger 에 없는 docHash 를 갱신하려 했다: $docHash")
        }
        transform(entity)
        entity.updatedAt = Instant.now()
        return repo.save(entity)
    }

    @Transactional(readOnly = true)
    fun find(docHash: String): DocumentRecordEntity? = repo.findById(docHash.lowercase()).orElse(null)

    @Transactional(readOnly = true)
    fun all(): List<DocumentRecordEntity> = repo.findAll()
}
