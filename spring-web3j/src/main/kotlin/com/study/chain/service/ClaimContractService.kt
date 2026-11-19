package com.study.chain.service

import com.study.chain.config.ChainProperties
import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt

/**
 * 4주차 실습 "3. 3주차 컨트랙트를 로컬 블록체인에 배포" 를 수행하는 서비스.
 *
 * contracts/Claim.sol 은 3주차에서 Remix(Remix VM)로 손으로 눌러가며
 * 확인했던 바로 그 컨트랙트다. 여기서는 같은 바이트코드를 **Web3j 로**
 * Anvil 에 배포하고, 같은 동작(정상 호출 / minter 아닌 계정의 revert)을
 * 코드로 재현한다. ClaimRedeployTest 가 이 흐름을 검증한다.
 *
 * CoinContractService 와 구조가 거의 같다 — 다른 컨트랙트에도 같은
 * ABI 인코딩·배포·영수증 확인 패턴이 그대로 적용된다는 걸 보여준다.
 */
@Service
class ClaimContractService(
    private val web3j: Web3j,
    private val codec: ContractCodec,
    private val tx: ValueTransferService,
    private val props: ChainProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val bytecode: String by lazy {
        "0x" + ClassPathResource("contracts/Claim.bin").inputStream
            .bufferedReader().readText().trim()
    }

    /** to 가 비어 있는 배포 트랜잭션. deploy 할 때마다 새 컨트랙트가 생긴다 — 3주차에서 본 그대로. */
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
        log.info("Claim 배포 완료 address={} gasUsed={}", address, receipt.gasUsed)
        return address
    }

    /** insurer 상태 변수를 읽는다. 배포자의 주소와 같아야 한다. */
    fun insurer(contract: String): String {
        val fn = codec.insurerFunction()
        val raw = callFunction(contract, org.web3j.abi.FunctionEncoder.encode(fn))
        return codec.decodeSingle<Address>(fn, raw).value
    }

    /**
     * pay(to, amount). insurer 가 아닌 계정으로 부르면 require 가 막고
     * status=0x0 인 영수증이 돌아온다 — 3주차에서 Remix로 확인했던 동작과 같다.
     */
    fun pay(contract: String, from: Credentials, to: String, amount: BigInteger): TransactionReceipt =
        sendFunction(contract, from, codec.encodePay(to, amount))

    fun paidOf(contract: String, who: String): BigInteger {
        val fn = codec.paidOfFunction(who)
        val raw = callFunction(contract, org.web3j.abi.FunctionEncoder.encode(fn))
        return codec.decodeSingle<Uint256>(fn, raw).value
    }

    private fun sendFunction(contract: String, from: Credentials, data: String): TransactionReceipt {
        val nonce = web3j.ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val gasLimit = estimateGas(from.address, contract, data)

        val raw = org.web3j.crypto.RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, contract, BigInteger.ZERO, data,
        )
        val signed = org.web3j.crypto.TransactionEncoder.signMessage(raw, props.chainId, from)
        val hash = tx.sendRaw(org.web3j.utils.Numeric.toHexString(signed))
        return tx.awaitReceipt(hash)
    }

    private fun estimateGas(from: String, to: String, data: String): BigInteger {
        val call = Transaction.createEthCallTransaction(from, to, data)
        val resp = web3j.ethEstimateGas(call).send()
        if (resp.hasError()) {
            log.debug("가스 추정 실패(revert 예상) — 기본값 사용: {}", resp.error.message)
            return BigInteger.valueOf(200_000)
        }
        return resp.amountUsed.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
    }

    private fun callFunction(contract: String, data: String): String {
        val resp = web3j.ethCall(
            Transaction.createEthCallTransaction(null, contract, data),
            DefaultBlockParameterName.LATEST,
        ).send()
        if (resp.isReverted) error("eth_call revert: ${resp.revertReason}")
        return resp.value
    }
}
