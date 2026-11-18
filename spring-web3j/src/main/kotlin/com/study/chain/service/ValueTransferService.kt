package com.study.chain.service

import com.study.chain.config.ChainProperties
import java.math.BigDecimal
import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.utils.Convert
import org.web3j.utils.Numeric

/**
 * 교재 2부 "Transactions" — 토큰 전송 TX 를 만들고, 서명하고, 보낸다.
 *
 * 교재의 Klaytn TX 는 type: 'VALUE_TRANSFER' 를 명시하지만
 * 이더리움에는 타입 필드가 그런 식으로 쓰이지 않는다.
 * `to` 에 주소가 있고 `data` 가 비어 있으면 그게 곧 값 전송이다.
 *
 * 교재 대응
 *   caver.klay.accounts.signTransaction(tx, pk)  →  signOnly()
 *   caver.klay.sendSignedTransaction(raw)        →  sendRaw()
 *   caver.klay.sendTransaction(tx)               →  signAndSend()
 */
@Service
class ValueTransferService(
    private val web3j: Web3j,
    private val props: ChainProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 교재의 TX 객체 + 서명 결과에 대응한다. */
    data class SignedTx(
        val rawTransaction: String,
        val txHash: String,
        val nonce: BigInteger,
    )

    /**
     * ① 서명만 한다. 아직 네트워크에 나가지 않는다.
     *
     * 교재:
     *   caver.klay.accounts.signTransaction(tx, wallet[0].privateKey)
     *     → { rawTransaction, txHash, v, r, s }
     *
     * 서명은 전적으로 내 프로세스 안에서 일어난다. 개인키는 밖으로 안 나간다.
     * 이 단계까지는 되돌릴 수 있다 — 그냥 안 보내면 된다.
     */
    fun signOnly(from: Credentials, to: String, amountEther: String): SignedTx {
        val nonce = web3j.ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount
        val value = Convert.toWei(amountEther, Convert.Unit.ETHER).toBigInteger()
        val gasPrice = web3j.ethGasPrice().send().gasPrice

        // 단순 값 전송은 언제나 정확히 21,000 가스다 — 실행되는 코드가 없기 때문이다.
        val gasLimit = BigInteger.valueOf(21_000)

        val raw = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, to, value)
        val signed = TransactionEncoder.signMessage(raw, props.chainId, from)
        val hex = Numeric.toHexString(signed)

        return SignedTx(
            rawTransaction = hex,
            txHash = org.web3j.crypto.Hash.sha3(hex),
            nonce = nonce,
        )
    }

    /**
     * ② 서명된 것을 보낸다.
     *
     * 교재: caver.klay.sendSignedTransaction(signedTransaction.rawTransaction)
     *
     * ⚠ 여기가 경계선이다. 이 호출이 나가는 순간부터
     *   "보냈는데 결과를 모르는" 구간이 시작된다.
     *   타임아웃이 나도 체인에서는 처리되고 있을 수 있다.
     *   그래서 돌려받은 해시를 **즉시 저장**해야 나중에 조회로 진실을 알아낼 수 있다.
     *
     * @return 트랜잭션 해시. 영수증이 아니라 접수증이다.
     */
    fun sendRaw(rawTransaction: String): String {
        val resp = web3j.ethSendRawTransaction(rawTransaction).send()
        if (resp.hasError()) {
            // nonce too low / already known / insufficient funds 등이 여기로 온다.
            throw IllegalStateException("제출 거부: ${resp.error.message}")
        }
        log.debug("제출됨 txHash={}", resp.transactionHash)
        return resp.transactionHash
    }

    /**
     * ③ 서명 + 전송 + 영수증 대기를 한 번에.
     *
     * 교재: caver.klay.sendTransaction(tx)
     *
     * ⚠ 블로킹이다. 영수증이 나올 때까지 스레드가 묶인다.
     *   로컬 Anvil 은 즉시 끝나지만 실제 네트워크에서는 12초 이상 걸린다.
     *   HTTP 요청 처리 스레드에서 직접 부르면 안 된다 — 워커로 빼야 한다.
     */
    fun signAndSend(from: Credentials, to: String, amountEther: String): TransactionReceipt {
        val signed = signOnly(from, to, amountEther)
        val hash = sendRaw(signed.rawTransaction)
        return awaitReceipt(hash)
    }

    /**
     * 영수증을 기다린다. 교재의 .on('receipt', ...) 에 대응한다.
     *
     * 교재 설명 그대로 — "TX 체결 시 Receipt 를 반환; 소요된 Gas, TX 해시,
     * input, output 등이 기록" 된다.
     */
    fun awaitReceipt(txHash: String): TransactionReceipt {
        repeat(props.receiptPollAttempts) {
            val r = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (r.isPresent) return r.get()
            Thread.sleep(props.receiptPollIntervalMs)
        }
        // 여기 오면 '실패'가 아니라 '모름'이다. 재시도하면 이중 집행이 될 수 있다.
        throw IllegalStateException("영수증 미도착 — 상태를 UNKNOWN 으로 두고 사람이 확인해야 한다. txHash=$txHash")
    }

    /**
     * 영수증이 성공인지 확인한다.
     *
     * ⚠ 가장 많이 놓치는 부분 — require 실패(revert)는 **예외로 오지 않는다.**
     *   HTTP 200 에 정상 영수증이 오고 status 만 0x0 이다.
     *   try-catch 로는 절대 못 잡는다.
     */
    fun isSuccess(receipt: TransactionReceipt): Boolean = receipt.status == "0x1"

    fun requireSuccess(receipt: TransactionReceipt): TransactionReceipt {
        check(isSuccess(receipt)) {
            "트랜잭션이 revert 됨. txHash=${receipt.transactionHash} gasUsed=${receipt.gasUsed}"
        }
        return receipt
    }

    fun toEther(wei: BigInteger): BigDecimal =
        Convert.fromWei(wei.toBigDecimal(), Convert.Unit.ETHER)
}
