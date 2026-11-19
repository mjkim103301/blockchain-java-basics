package com.study.chain.service

import java.security.MessageDigest
import org.springframework.stereotype.Component
import org.web3j.utils.Numeric

/**
 * 문서 원본을 SHA-256 으로 해시한다. 1주차 Hashes.sha256 과 원리가 같다
 * (java.security.MessageDigest, 외부 라이브러리 없음).
 *
 * SHA-256 을 고른 이유 — 결과가 정확히 32바이트라서 Solidity 의
 * bytes32 타입에 변환 없이 그대로 들어간다. (이더리움 네이티브 해시인
 * keccak256 을 써도 되지만, "문서 지문"은 플랫폼과 무관하게 SHA-256 을
 * 쓰는 게 관례다 — PDF 전자서명, 공인전자문서 등이 전부 SHA-256 이다.)
 */
@Component
class DocumentHasher {

    /** 바이트 배열을 그대로 해시한다. 32바이트가 나온다. */
    fun sha256(content: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(content)

    /** 0x 로 시작하는 64자리 16진 문자열로 바꾼다. bytes32 를 사람이 읽는 형태. */
    fun toHex(hash: ByteArray): String = Numeric.toHexString(hash)

    /** "0x1234…" 를 다시 32바이트로. REST 에서 docHash 를 받을 때 쓴다. */
    fun fromHex(hex: String): ByteArray {
        val bytes = Numeric.hexStringToByteArray(hex)
        require(bytes.size == 32) { "docHash 는 32바이트(64자리 16진수)여야 한다. 받은 크기=${bytes.size}" }
        return bytes
    }
}
