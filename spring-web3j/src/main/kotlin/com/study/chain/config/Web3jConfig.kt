package com.study.chain.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.response.PollingTransactionReceiptProcessor

/**
 * 교재의 아래 두 줄에 대응한다.
 *
 *   const Caver  = require('caver-js');
 *   const caver  = new Caver('https://api.baobab.klaytn.net:8651/');
 *
 * Web3j 는 서버가 아니라 라이브러리다. 내 JVM 안에서 돌고,
 * 하는 일은 ABI 인코딩·서명·HTTP 전송뿐이다.
 */
@Configuration
class Web3jConfig(private val props: ChainProperties) {

    /** JSON-RPC 클라이언트. 이것만 있으면 읽기는 전부 된다. */
    @Bean
    fun web3j(): Web3j = Web3j.build(HttpService(props.rpcUrl))

    /**
     * 교재의 caver.klay.accounts.wallet 에 들어가는 어카운트에 해당한다.
     * 개인키를 들고 있으며 서명할 때 쓴다. 노드로 넘어가지 않는다.
     */
    @Bean
    fun credentials(): Credentials = Credentials.create(props.privateKey)

    /**
     * nonce 조회 → 서명 → 제출을 담당한다.
     *
     * chainId 를 넣는 게 중요하다. EIP-155 리플레이 보호가 걸려서
     * 테스트넷에서 서명한 트랜잭션이 메인넷에서 재사용되지 않는다.
     *
     * 주의 — 이 매니저는 매번 eth_getTransactionCount 로 nonce 를 읽는다.
     * 인스턴스가 둘 이상이고 같은 지갑을 쓰면 같은 nonce 를 받아 충돌한다.
     * 제출 워커는 단일 인스턴스로 돌려야 한다.
     */
    @Bean
    fun transactionManager(web3j: Web3j, credentials: Credentials): TransactionManager =
        RawTransactionManager(
            web3j,
            credentials,
            props.chainId,
            PollingTransactionReceiptProcessor(
                web3j,
                props.receiptPollIntervalMs,
                props.receiptPollAttempts,
            ),
        )
}
