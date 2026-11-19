plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    // @Entity 클래스에 자동으로 open + no-arg 생성자를 만들어 준다.
    // Kotlin 클래스는 기본이 final 인데, Hibernate 는 프록시를 만들려고
    // 상속이 필요하다 — 이 플러그인이 그 간극을 메운다.
    kotlin("plugin.jpa") version "2.1.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.study"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    // 4주차 핵심 — JSON-RPC 를 Java 객체로 감싼 SDK
    implementation("org.web3j:core:4.14.0")

    // 담당 C 몫 — 트랜잭션 상태를 PostgreSQL 에 영속화한다.
    // (온체인 데이터가 아니라 "우리 쪽에서 무엇을 보냈고 어떻게 됐는지"의 기록이다.
    //  이게 바로 web3j-spring 문서에서 다룬 Outbox 테이블의 실물이다.)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
