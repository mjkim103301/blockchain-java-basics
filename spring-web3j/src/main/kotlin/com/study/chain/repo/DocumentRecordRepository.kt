package com.study.chain.repo

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 가 구현체를 자동 생성한다. 메서드를 더 추가하면
 * (예: findByStatus) 쿼리도 자동으로 만들어진다 — 이게 "인덱서가 만들어
 * 주는 검색 가능한 사본"의 가장 단순한 형태다. 체인 자체에는
 * WHERE/ORDER BY 가 없다는 걸 3·4주차에서 반복해 다뤘다.
 */
interface DocumentRecordRepository : JpaRepository<DocumentRecordEntity, String>
