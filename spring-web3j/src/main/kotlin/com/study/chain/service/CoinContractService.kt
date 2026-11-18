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
 * 교재 3·4부 — 스마트 컨트랙트를 배포하고, 함수를 실행하고, 상태를 읽는다.
 *
 * 교재 대응
 *   new caver.klay.Contract(abi)
 *     .deploy({ data: bytecode }).send({...})     →  deploy()
 *   contract.methods.mint(a, 100).send({...})     →  mint()   (mutation)
 *   contract.methods.balances(a).call()           →  balanceOf()  (constant)
 *   Coin.Sent().watch(...)                        →  readSentEvents()
 *
 * 교재가 강조하는 구분이 여기서 그대로 드러난다.
 *
 *   상태를 바꾸는 함수  →  TX 를 만들어 블록에 추가해야 한다 (가스 소모)
 *   상태를 읽는 함수    →  TX 가 필요 없다. 노드에서 바로 실행 (가스 0)
 */
@Service
class CoinContractService(
    private val web3j: Web3j,
    private val codec: ContractCodec,
    private val tx: ValueTransferService,
    private val props: ChainProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 교재: 컴파일 결과인 .bin 파일 내용. 배포 TX 의 data 자리에 들어간다. */
    private val bytecode: String by lazy {
        "0x" + ClassPathResource("contracts/Coin.bin").inputStream
            .bufferedReader().readText().trim()
    }

    // ------------------------------------------------------------- 배포

    /**
     * 교재:
     *   contract.deploy({ data: '6080604052...' })
     *          .send({ from, gas, value: 0 })
     *          .on('receipt', r => r.contractAddress)
     *
     * 배포 TX 는 **to 가 비어 있는** 트랜잭션이다. 그게 "새 컨트랙트를 만들라"는 신호다.
     * 컨트랙트 주소는 배포자 주소 + nonce 로 계산되므로,
     * 같은 계정으로 또 배포하면 반드시 다른 주소가 나온다.
     */
    fun deploy(deployer: Credentials): String {
        val nonce = web3j.ethGetTransactionCount(deployer.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount
        val gasPrice = web3j.ethGasPrice().send().gasPrice

        val raw = org.web3j.crypto.RawTransaction.createContractTransaction(
            nonce, gasPrice, BigInteger.valueOf(3_000_000), BigInteger.ZERO, bytecode,
        )
        val signed = org.web3j.crypto.TransactionEncoder.signMessage(raw, props.chainId, deployer)
        val hash = tx.sendRaw(org.web3j.utils.Numeric.toHexString(signed))

        val receipt = tx.requireSuccess(tx.awaitReceipt(hash))
        val address = receipt.contractAddress
            ?: error("영수증에 contractAddress 가 없다. 배포 TX 가 아니었을 수 있다.")

        log.info("Coin 배포 완료 address={} gasUsed={}", address, receipt.gasUsed)
        return address
    }

    // --------------------------------------------- 상태를 바꾸는 함수 (TX)

    /**
     * 교재: contract.methods.mint(receiver, amount).send({ from, gas })
     *
     * minter 가 아닌 계정으로 부르면 require 가 막는다.
     * 그때 **예외가 아니라 status=0x0 인 영수증**이 돌아온다.
     * requireSuccess() 가 그걸 잡아 준다.
     */
    fun mint(contract: String, from: Credentials, receiver: String, amount: BigInteger):
        TransactionReceipt = sendFunction(contract, from, codec.encodeMint(receiver, amount))

    /** 교재: contract.methods.send(receiver, amount).send({ from, gas }) */
    fun send(contract: String, from: Credentials, receiver: String, amount: BigInteger):
        TransactionReceipt = sendFunction(contract, from, codec.encodeSend(receiver, amount))

    private fun sendFunction(contract: String, from: Credentials, data: String): TransactionReceipt {
        val nonce = web3j.ethGetTransactionCount(from.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount
        val gasPrice = web3j.ethGasPrice().send().gasPrice

        // 교재가 gas: 300000 으로 고정한 자리다.
        // 실무에서는 eth_estimateGas 로 추정하고 여유를 둔다.
        val gasLimit = estimateGas(from.address, contract, data)

        val raw = org.web3j.crypto.RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, contract, BigInteger.ZERO, data,
        )
        val signed = org.web3j.crypto.TransactionEncoder.signMessage(raw, props.chainId, from)
        val hash = tx.sendRaw(org.web3j.utils.Numeric.toHexString(signed))
        return tx.awaitReceipt(hash)
    }

    /**
     * eth_estimateGas. 실패할 수도 있는데 (revert 가 예상되면 추정 자체가 실패한다)
     * 그때는 넉넉한 기본값으로 넘긴다 — 실제 revert 를 영수증으로 확인하기 위해서다.
     */
    private fun estimateGas(from: String, to: String, data: String): BigInteger {
        val call = Transaction.createEthCallTransaction(from, to, data)
        val resp = web3j.ethEstimateGas(call).send()
        if (resp.hasError()) {
            log.debug("가스 추정 실패(revert 예상) — 기본값 사용: {}", resp.error.message)
            return BigInteger.valueOf(300_000)
        }
        // 추정값은 '그 시점' 기준이라 20% 여유를 둔다.
        return resp.amountUsed.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
    }

    // ------------------------------------------ 상태를 읽는 함수 (eth_call)

    /**
     * 교재: contract.methods.balances(addr).call()
     *
     * 트랜잭션이 아니다. 노드 한 대가 혼자 실행해 보고 결과만 돌려준다.
     * 체인에는 아무 흔적도 남지 않고 가스도 들지 않는다.
     */
    fun balanceOf(contract: String, owner: String): BigInteger {
        val fn = codec.balancesFunction(owner)
        val raw = callFunction(contract, org.web3j.abi.FunctionEncoder.encode(fn))
        return codec.decodeSingle<Uint256>(fn, raw).value
    }

    /** 교재: contract.methods.minter().call() */
    fun minter(contract: String): String {
        val fn = codec.minterFunction()
        val raw = callFunction(contract, org.web3j.abi.FunctionEncoder.encode(fn))
        return codec.decodeSingle<Address>(fn, raw).value
    }

    private fun callFunction(contract: String, data: String): String {
        val resp = web3j.ethCall(
            Transaction.createEthCallTransaction(null, contract, data),
            DefaultBlockParameterName.LATEST,
        ).send()
        if (resp.isReverted) error("eth_call revert: ${resp.revertReason}")
        return resp.value
    }

    // ------------------------------------------------------------ 이벤트

    data class SentEvent(
        val from: String,
        val to: String,
        val amount: BigInteger,
        val blockNumber: BigInteger,
        val txHash: String,
    )

    /**
     * 교재: Coin.Sent().watch({}, '', function(err, result) { ... })
     *
     * 영수증의 로그에서 Sent 이벤트를 꺼낸다.
     * indexed 로 선언한 from/to 는 topics 에, amount 는 data 에 들어 있다.
     *
     * 체인에는 WHERE 나 ORDER BY 가 없다. 이 로그를 긁어 일반 DB 에 쌓는 게
     * 인덱서가 하는 일이고, 그래야 비로소 검색이 가능해진다.
     */
    fun sentEventsOf(receipt: TransactionReceipt): List<SentEvent> {
        val event = org.web3j.abi.datatypes.Event(
            "Sent",
            listOf(
                object : org.web3j.abi.TypeReference<Address>(true) {},
                object : org.web3j.abi.TypeReference<Address>(true) {},
                object : org.web3j.abi.TypeReference<Uint256>() {},
            ),
        )
        val topic0 = org.web3j.abi.EventEncoder.encode(event)

        return receipt.logs
            .filter { it.topics.isNotEmpty() && it.topics[0].equals(topic0, ignoreCase = true) }
            .map { logEntry ->
                val indexed = org.web3j.abi.FunctionReturnDecoder.decodeIndexedValue(
                    logEntry.topics[1], object : org.web3j.abi.TypeReference<Address>() {},
                )
                val indexed2 = org.web3j.abi.FunctionReturnDecoder.decodeIndexedValue(
                    logEntry.topics[2], object : org.web3j.abi.TypeReference<Address>() {},
                )
                val nonIndexed = org.web3j.abi.FunctionReturnDecoder.decode(
                    logEntry.data, event.nonIndexedParameters,
                )
                SentEvent(
                    from = indexed.value as String,
                    to = indexed2.value as String,
                    amount = (nonIndexed[0] as Uint256).value,
                    blockNumber = logEntry.blockNumber,
                    txHash = logEntry.transactionHash,
                )
            }
    }
}
