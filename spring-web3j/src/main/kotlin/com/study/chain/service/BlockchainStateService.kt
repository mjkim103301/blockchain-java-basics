package com.study.chain.service

import java.math.BigInteger
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Convert

/**
 * 교재 1부 "Blockchain State" — 블록체인은 트랜잭션으로 변화하는 상태 기계다.
 *
 * 여기 있는 건 전부 **읽기(eth_call 계열)** 라서 가스가 들지 않고 즉시 응답한다.
 *
 * 교재 대응
 *   caver.klay.getBlockNumber()  →  web3j.ethBlockNumber()
 *   caver.klay.getBalance(addr)  →  web3j.ethGetBalance(addr, LATEST)
 */
@Service
class BlockchainStateService(private val web3j: Web3j) {

    /** 교재: caver.klay.getBlockNumber().then(console.log) */
    fun blockNumber(): BigInteger =
        web3j.ethBlockNumber().send().blockNumber

    /** 체인 ID. 어느 네트워크에 붙었는지 확인하는 용도. */
    fun chainId(): BigInteger =
        web3j.ethChainId().send().chainId

    /** 클라이언트 버전. Anvil 인지 Geth 인지 여기서 드러난다. */
    fun clientVersion(): String =
        web3j.web3ClientVersion().send().web3ClientVersion

    /**
     * 잔액. 단위는 wei 다.
     *
     * 교재의 caver.utils.toPeb('1','KLAY') 가 Klaytn 의 단위 변환인데,
     * Web3j 에서는 Convert.toWei / fromWei 가 같은 일을 한다.
     * 1 ETH = 10^18 wei 이므로 Long 으로는 넘친다. 반드시 BigInteger 다.
     */
    fun balanceWei(address: String): BigInteger =
        web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().balance

    fun balanceEther(address: String): java.math.BigDecimal =
        Convert.fromWei(balanceWei(address).toBigDecimal(), Convert.Unit.ETHER)

    /**
     * 계정의 nonce — 이 주소가 지금까지 보낸 트랜잭션 수.
     *
     * 장애 복구에서 쓸모가 크다. "보냈는지 안 보냈는지 모르는" 상황에서
     * nonce 가 올라갔는지를 보면 제출 여부를 역으로 알아낼 수 있다.
     */
    fun nonce(address: String): BigInteger =
        web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST)
            .send().transactionCount
}
