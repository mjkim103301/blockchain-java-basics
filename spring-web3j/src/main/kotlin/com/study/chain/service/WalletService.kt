package com.study.chain.service

import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys

/**
 * 교재 "klay.accounts.wallet" 절에 대응한다.
 *
 *   const account = caver.klay.accounts.create();
 *   const wallet  = caver.klay.accounts.wallet;
 *   wallet.add(account);
 *
 * caver-js 는 in-memory wallet 을 SDK 가 제공하지만 Web3j 에는 없다.
 * Credentials 를 직접 들고 다니면 된다 — 사실 그게 전부다.
 *
 * ⚠ 이 클래스는 실습용이다. 운영에서 개인키를 메모리 Map 에 들고 있으면 안 된다.
 */
@Service
class WalletService {

    private val wallet = linkedMapOf<String, Credentials>()

    /**
     * 교재: caver.klay.accounts.create()
     *
     * 개인키 생성에 계산이 거의 필요 없다는 점이 핵심이다.
     * 사실상 256비트 난수를 뽑는 것이고, 비밀이 지켜지는 건
     * 어려워서가 아니라 경우의 수가 커서다.
     */
    fun create(): Credentials =
        Credentials.create(Keys.createEcKeyPair()).also { wallet[it.address.lowercase()] = it }

    /** 교재: wallet.add(account) */
    fun add(privateKey: String): Credentials =
        Credentials.create(privateKey).also { wallet[it.address.lowercase()] = it }

    /** 교재: wallet[address] */
    fun get(address: String): Credentials? = wallet[address.lowercase()]

    /** 교재: wallet[0] */
    fun at(index: Int): Credentials? = wallet.values.elementAtOrNull(index)

    /** 교재: wallet.length */
    fun size(): Int = wallet.size

    /** 교재: wallet.clear() */
    fun clear() = wallet.clear()

    fun addresses(): List<String> = wallet.values.map { it.address }
}
