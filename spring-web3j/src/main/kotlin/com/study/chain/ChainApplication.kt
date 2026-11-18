package com.study.chain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * 블록체인 스터디 4주차 — Spring Boot + Web3j 연동.
 *
 * 교재 2강(Understanding Account-based Blockchain)의 caver-js 예제를
 * 그대로 Web3j 로 옮긴 것이다. 교재는 Klaytn/Baobab 을 쓰지만
 * 그 테스트넷은 현재 운영되지 않으므로 로컬 체인(Anvil)에 붙는다.
 * JSON-RPC 규격이 같아서 코드는 그대로 통한다.
 *
 * 실행 순서
 *   1) bash scripts/anvil.sh        ← 로컬 체인
 *   2) ./gradlew bootRun            ← 이 앱
 *   3) curl localhost:8080/api/chain/state
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class ChainApplication

fun main(args: Array<String>) {
    runApplication<ChainApplication>(*args)
}
