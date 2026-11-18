package com.study.chain.service

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
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

    // ------------------------------------------------------------ 공통

    /** 함수 선택자 4바이트만 뽑는다. Etherscan 의 Input Data 맨 앞에 보이는 그것. */
    fun selectorOf(function: Function): String =
        FunctionEncoder.encode(function).take(10)   // "0x" + 8자리

    /** eth_call 의 반환 바이트열을 ABI 로 디코딩한다. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Type<*>> decodeSingle(function: Function, rawValue: String): T {
        val decoded: List<Type<*>> =
            FunctionReturnDecoder.decode(rawValue, function.outputParameters)
        check(decoded.isNotEmpty()) { "디코딩 결과가 비어 있다. revert 되었을 수 있다. raw=$rawValue" }
        return decoded[0] as T
    }
}
