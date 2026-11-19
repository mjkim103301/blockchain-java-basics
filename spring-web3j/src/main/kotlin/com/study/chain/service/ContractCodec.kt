package com.study.chain.service

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.springframework.stereotype.Component
import java.math.BigInteger

/**
 * 교재 "Bytecode & ABI" 절을 코드로 만든 것.
 *
 *   ABI 는 컨트랙트 함수를 JSON 형태로 표현한 정보로
 *   EVM 이 컨트랙트 함수를 실행할 때 필요하다.
 *
 * web3j CLI 로 래퍼 클래스를 생성하는 방법도 있지만, 여기서는 일부러
 * **Function / FunctionEncoder 를 직접** 쓴다. 래퍼를 쓰면 ABI 인코딩이
 * 눈에 안 보이는데, 그게 이번 주차에 봐야 할 바로 그 부분이기 때문이다.
 *
 * 인코딩 결과는 이렇게 생겼다.
 *
 *   mint(0xf39F…, 100)
 *     → 0x40c10f19                                              ← 함수 선택자 4바이트
 *       000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb92266   ← 인자 1 (32바이트)
 *       0000000000000000000000000000000000000000000000000000000000000064   ← 인자 2 (32바이트)
 *
 * 선택자는 keccak256("mint(address,uint256)") 의 앞 4바이트다.
 * 체인에는 함수 이름이 저장돼 있지 않고 이 4바이트로만 구분된다.
 */
@Component
class ContractCodec {

    // ------------------------------------------------------------ Coin

    fun encodeMint(receiver: String, amount: BigInteger): String =
        FunctionEncoder.encode(
            Function("mint", listOf(Address(receiver), Uint256(amount)), emptyList())
        )

    fun encodeSend(receiver: String, amount: BigInteger): String =
        FunctionEncoder.encode(
            Function("send", listOf(Address(receiver), Uint256(amount)), emptyList())
        )

    /** view 함수. 반환 타입을 알려줘야 디코딩할 수 있다. */
    fun balancesFunction(owner: String): Function =
        Function(
            "balances",
            listOf(Address(owner)),
            listOf(object : TypeReference<Uint256>() {}),
        )

    fun minterFunction(): Function =
        Function("minter", emptyList(), listOf(object : TypeReference<Address>() {}))

    // --------------------------------------------------- SimpleStorage

    fun encodeSet(x: BigInteger): String =
        FunctionEncoder.encode(Function("set", listOf(Uint256(x)), emptyList()))

    fun getFunction(): Function =
        Function("get", emptyList(), listOf(object : TypeReference<Uint256>() {}))

    // -------------------------------------------- Claim (3주차 컨트랙트)

    /**
     * 3주차 Claim.sol 의 함수들. Remix 에서 손으로 눌렀던 pay/paidOf 를
     * 여기서는 Web3j 로 인코딩한다 — 선택자가 같으면 어느 쪽에서 호출하든
     * 체인 입장에서는 똑같은 호출이다.
     */
    fun encodePay(to: String, amount: BigInteger): String =
        FunctionEncoder.encode(Function("pay", listOf(Address(to), Uint256(amount)), emptyList()))

    fun paidOfFunction(who: String): Function =
        Function("paidOf", listOf(Address(who)), listOf(object : TypeReference<Uint256>() {}))

    fun insurerFunction(): Function =
        Function("insurer", emptyList(), listOf(object : TypeReference<Address>() {}))

    // --------------------------------------- DocumentRegistry (4주차)

    /**
     * register(bytes32) / verify(bytes32).
     *
     * 문서 "내용"이 아니라 문서의 SHA-256 "해시"가 인자로 들어간다는 점이
     * mint/pay 와 다른 전부다. bytes32 타입은 정확히 32바이트만 받는다 —
     * SHA-256 결과가 정확히 32바이트라서 변환 없이 그대로 들어맞는다.
     */
    fun encodeRegister(docHash: ByteArray): String {
        require(docHash.size == 32) { "docHash 는 32바이트여야 한다 (SHA-256 결과). size=${docHash.size}" }
        return FunctionEncoder.encode(Function("register", listOf(Bytes32(docHash)), emptyList()))
    }

    /** verify 는 4개짜리 튜플(bool,address,uint256,uint256)을 돌려준다. decodeAll() 로 받는다. */
    fun verifyFunction(docHash: ByteArray): Function {
        require(docHash.size == 32) { "docHash 는 32바이트여야 한다 (SHA-256 결과). size=${docHash.size}" }
        return Function(
            "verify",
            listOf(Bytes32(docHash)),
            listOf(
                object : TypeReference<Bool>() {},
                object : TypeReference<Address>() {},
                object : TypeReference<Uint256>() {},
                object : TypeReference<Uint256>() {},
            ),
        )
    }

    // ------------------------------------------------------------ 공통

    /** 함수 선택자 4바이트만 뽑는다. Etherscan 의 Input Data 맨 앞에 보이는 그것. */
    fun selectorOf(function: Function): String =
        FunctionEncoder.encode(function).take(10)   // "0x" + 8자리

    /** eth_call 의 반환 바이트열을 ABI 로 디코딩한다 — 반환값이 하나일 때. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Type<*>> decodeSingle(function: Function, rawValue: String): T {
        val decoded = decodeAll(function, rawValue)
        return decoded[0] as T
    }

    /** 반환값이 여러 개(튜플)일 때. verify() 처럼 (bool,address,uint256,uint256) 를 받을 때 쓴다. */
    fun decodeAll(function: Function, rawValue: String): List<Type<*>> {
        val decoded: List<Type<*>> =
            FunctionReturnDecoder.decode(rawValue, function.outputParameters)
        check(decoded.isNotEmpty()) { "디코딩 결과가 비어 있다. revert 되었을 수 있다. raw=$rawValue" }
        return decoded
    }
}
