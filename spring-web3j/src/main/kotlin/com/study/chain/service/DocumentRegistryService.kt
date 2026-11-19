package com.study.chain.service

import com.study.chain.config.ChainProperties
import com.study.chain.repo.DocumentRecordEntity
import com.study.chain.repo.DocumentTxStatus
import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction

/**
 * 4주차 실습 "전자계약 문서 무결성 검증 API" 의 핵심 서비스.
 *
 * contracts/DocumentRegistry.sol 을 배포하고, 문서 해시를 등록하고,
 * 등록 여부로 변조를 검증한다. 패턴은 CoinContractService·
 * ClaimContractService 와 같다 — 배포 / 상태 변경(register) / 읽기(verify).
 *
 * 다른 점은 딱 하나다. register() 가 **영수증을 기다리기 전에 txHash 를
 * 먼저 저장**한다. 「Spring Boot와 체인 잇기」에서 다룬 "②번이 최악이다 —
 * 제출 직후, 해시를 저장하기 전에 죽으면 보냈는지조차 모르게 된다" 는
 * 그 교훈을 코드로 반영한 것이다.
 */
@Service
class DocumentRegistryService(
    private val web3j: Web3j,
    private val codec: ContractCodec,
    private val tx: ValueTransferService,
    private val props: ChainProperties,
    private val ledger: DocumentLedger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val bytecode: String by lazy {
        "0x" + ClassPathResource("contracts/DocumentRegistry.bin").inputStream
            .bufferedReader().readText().trim()
    }

    // ------------------------------------------------------------- 배포

    fun deploy(deployer: Credentials): String {
        val nonce = web3j.ethGetTransactionCount(deployer.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount
        val gasPrice = web3j.ethGasPrice().send().gasPrice

        val raw = org.web3j.crypto.RawTransaction.createContractTransaction(
            nonce, gasPrice, BigInteger.valueOf(1_000_000), BigInteger.ZERO, bytecode,
        )
        val signed = org.web3j.crypto.TransactionEncoder.signMessage(raw, props.chainId, deployer)
        val hash = tx.sendRaw(org.web3j.utils.Numeric.toHexString(signed))

        val receipt = tx.requireSuccess(tx.awaitReceipt(hash))
        val address = receipt.contractAddress ?: error("배포 영수증에 contractAddress 가 없다.")
        log.info("DocumentRegistry 배포 완료 address={} gasUsed={}", address, receipt.gasUsed)
        return address
    }

    // -------------------------------------------------------- 등록 (TX)

    /**
     * 문서 해시를 체인에 등록한다.
     *
     * 순서가 중요하다.
     *   ① 서명 → ② 전송(txHash 받음) → ③ **ledger 에 SUBMITTED 로 즉시 저장**
     *   → ④ 영수증 대기 → ⑤ 결과에 따라 CONFIRMED/REVERTED/UNKNOWN 으로 갱신
     *
     * ③을 ④보다 먼저 하는 이유 — 영수증을 기다리는 동안(④) 서버가 죽어도
     * ③에서 저장해 둔 txHash 로 나중에 조회해 "진짜로 들어갔는지" 확인할 수
     * 있다. 반대로 하면(영수증까지 받은 다음에 저장) 그 사이에 죽었을 때
     * 보냈는지조차 알 길이 없어진다.
     */
    fun register(contract: String, from: Credentials, docHash: ByteArray): DocumentRecordEntity {
        val docHashHex = org.web3j.utils.Numeric.toHexString(docHash)
        val data = codec.encodeRegister(docHash)

        val nonce = web3j.ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val gasLimit = estimateGas(from.address, contract, data)

        val raw = org.web3j.crypto.RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, contract, BigInteger.ZERO, data,
        )
        val signed = org.web3j.crypto.TransactionEncoder.signMessage(raw, props.chainId, from)
        val txHash = tx.sendRaw(org.web3j.utils.Numeric.toHexString(signed))

        // ⚠ 영수증을 기다리기 '전에' PostgreSQL 에 저장한다 — 이 서비스에서 제일 중요한 한 줄이다.
        // DB 쓰기는 이 한 줄로 즉시 커밋된다(DocumentLedger.save 가 @Transactional).
        // 아래 awaitReceipt() 가 아무리 오래 걸리거나 터져도 이 기록은 이미 안전하다.
        ledger.save(
            docHash = docHashHex,
            contractAddress = contract,
            txHash = txHash,
            registrant = from.address,
            status = DocumentTxStatus.SUBMITTED,
        )

        val receipt = try {
            tx.awaitReceipt(txHash)
        } catch (e: IllegalStateException) {
            log.warn("영수증 미도착 — docHash={} txHash={} 를 UNKNOWN 으로 남긴다", docHashHex, txHash)
            return ledger.update(docHashHex) { it.status = DocumentTxStatus.UNKNOWN }
        }

        return if (tx.isSuccess(receipt)) {
            ledger.update(docHashHex) {
                it.status = DocumentTxStatus.CONFIRMED
                it.blockNumber = receipt.blockNumber.toLong()
                it.gasUsed = receipt.gasUsed.toLong()
            }
        } else {
            // 가장 흔한 revert 사유 — "Document already registered."
            val reason = runCatching { tx.revertReasonOf(receipt, data) }.getOrNull()
            ledger.update(docHashHex) {
                it.status = DocumentTxStatus.REVERTED
                it.gasUsed = receipt.gasUsed.toLong()
                it.revertReason = reason
            }
        }
    }

    private fun estimateGas(from: String, to: String, data: String): BigInteger {
        val call = Transaction.createEthCallTransaction(from, to, data)
        val resp = web3j.ethEstimateGas(call).send()
        if (resp.hasError()) {
            log.debug("가스 추정 실패(revert 예상) — 기본값 사용: {}", resp.error.message)
            return BigInteger.valueOf(150_000)
        }
        return resp.amountUsed.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
    }

    // --------------------------------------------------- 검증 (eth_call)

    data class VerifyResult(
        val exists: Boolean,
        val registrant: String,
        val registeredAt: BigInteger,
        val blockNumber: BigInteger,
    )

    /**
     * 문서 해시로 등록 여부를 확인한다. 가스가 들지 않는다.
     *
     * exists=false 가 "문서가 변조됐다"는 뜻이 되려면 전제가 하나 있다 —
     * 호출한 쪽이 "원래 등록했던 해시"가 아니라 "지금 손에 든 문서를 다시
     * 해시한 값"을 넘겼어야 한다. 그 비교는 DocumentController 에서 한다.
     */
    fun verify(contract: String, docHash: ByteArray): VerifyResult {
        val fn = codec.verifyFunction(docHash)
        val raw = callFunction(contract, org.web3j.abi.FunctionEncoder.encode(fn))
        val decoded = codec.decodeAll(fn, raw)
        return VerifyResult(
            exists = (decoded[0] as Bool).value,
            registrant = (decoded[1] as Address).value,
            registeredAt = (decoded[2] as Uint256).value,
            blockNumber = (decoded[3] as Uint256).value,
        )
    }

    private fun callFunction(contract: String, data: String): String {
        val resp = web3j.ethCall(
            Transaction.createEthCallTransaction(null, contract, data),
            DefaultBlockParameterName.LATEST,
        ).send()
        if (resp.isReverted) error("eth_call revert: ${resp.revertReason}")
        return resp.value
    }

    // ---------------------------------------------------------- 원장 조회

    /** PostgreSQL 에 쌓인 "우리 쪽 기록"만 조회한다. 체인을 다시 묻지 않는다 — 가스도 네트워크 호출도 없다. */
    fun ledgerOf(docHashHex: String): DocumentRecordEntity? = ledger.find(docHashHex)
}
