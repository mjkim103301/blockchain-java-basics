package com.study.chain.web

import com.study.chain.service.*
import java.math.BigInteger
import org.springframework.web.bind.annotation.*
import org.web3j.crypto.Credentials

/**
 * 교재 내용을 브라우저/curl 로 하나씩 확인해 보는 엔드포인트.
 *
 *   curl localhost:8080/api/chain/state
 *   curl -X POST localhost:8080/api/chain/coin/deploy
 *   curl -X POST "localhost:8080/api/chain/coin/{addr}/mint?to=0x..&amount=100"
 *   curl "localhost:8080/api/chain/coin/{addr}/balance?owner=0x.."
 *
 * ⚠ 쓰기 엔드포인트는 영수증이 나올 때까지 **스레드를 붙잡는다.**
 *   로컬 Anvil 이라 즉시 끝나지만, 실제 네트워크라면 이렇게 만들면 안 된다.
 *   Outbox 테이블에 적어 두고 별도 워커가 처리하게 빼야 한다.
 */
@RestController
@RequestMapping("/api/chain")
class StudyController(
    private val state: BlockchainStateService,
    private val wallet: WalletService,
    private val transfer: ValueTransferService,
    private val coin: CoinContractService,
    private val credentials: Credentials,
) {

    @GetMapping("/state")
    fun state(): Map<String, Any> = mapOf(
        "clientVersion" to state.clientVersion(),
        "chainId" to state.chainId(),
        "blockNumber" to state.blockNumber(),
        "deployer" to credentials.address,
        "balanceEther" to state.balanceEther(credentials.address),
        "nonce" to state.nonce(credentials.address),
    )

    @PostMapping("/accounts")
    fun createAccount(): Map<String, Any> {
        val c = wallet.create()
        return mapOf("address" to c.address, "walletSize" to wallet.size())
    }

    @GetMapping("/accounts")
    fun accounts(): Map<String, Any> =
        mapOf("size" to wallet.size(), "addresses" to wallet.addresses())

    @PostMapping("/transfer")
    fun transfer(@RequestParam to: String, @RequestParam amount: String): Map<String, Any?> {
        val receipt = transfer.signAndSend(credentials, to, amount)
        return mapOf(
            "txHash" to receipt.transactionHash,
            "status" to receipt.status,
            "success" to transfer.isSuccess(receipt),
            "gasUsed" to receipt.gasUsed,
            "blockNumber" to receipt.blockNumber,
        )
    }

    @PostMapping("/coin/deploy")
    fun deploy(): Map<String, Any> {
        val address = coin.deploy(credentials)
        return mapOf("contractAddress" to address, "minter" to coin.minter(address))
    }

    @PostMapping("/coin/{contract}/mint")
    fun mint(
        @PathVariable contract: String,
        @RequestParam to: String,
        @RequestParam amount: Long,
    ): Map<String, Any?> {
        val receipt = coin.mint(contract, credentials, to, BigInteger.valueOf(amount))
        return mapOf(
            "txHash" to receipt.transactionHash,
            "success" to transfer.isSuccess(receipt),
            "gasUsed" to receipt.gasUsed,
            "balanceOfTo" to coin.balanceOf(contract, to),
        )
    }

    @PostMapping("/coin/{contract}/send")
    fun send(
        @PathVariable contract: String,
        @RequestParam to: String,
        @RequestParam amount: Long,
    ): Map<String, Any?> {
        val receipt = coin.send(contract, credentials, to, BigInteger.valueOf(amount))
        return mapOf(
            "txHash" to receipt.transactionHash,
            "success" to transfer.isSuccess(receipt),
            "events" to coin.sentEventsOf(receipt),
        )
    }

    @GetMapping("/coin/{contract}/balance")
    fun balance(@PathVariable contract: String, @RequestParam owner: String): Map<String, Any> =
        mapOf("owner" to owner, "balance" to coin.balanceOf(contract, owner))
}
