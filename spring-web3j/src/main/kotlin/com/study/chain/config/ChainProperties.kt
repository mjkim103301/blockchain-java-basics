package com.study.chain.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * application.yml 의 chain.* 설정.
 *
 * 개인키가 설정 파일에 있는 건 로컬 실습이라서다.
 * 운영에서는 HSM 이나 KMS 로 빼야 한다 — 키가 유출되면 그 주소의 자산과
 * 권한이 전부 날아가고 재발급이라는 개념 자체가 없다.
 */
@ConfigurationProperties(prefix = "chain")
data class ChainProperties(
    val rpcUrl: String,
    val chainId: Long,
    val privateKey: String,
    val receiptPollIntervalMs: Long = 300,
    val receiptPollAttempts: Int = 40,
)
