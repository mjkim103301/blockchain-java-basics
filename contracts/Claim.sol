// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * 스터디 3주차 실습용 컨트랙트.
 *
 * 목적은 '잘 만든 보험금 컨트랙트'가 아니라, 아래 다섯 가지를
 * Remix 에서 눈으로 확인하는 것이다.
 *
 *   1. 배포는 수정이 아니라 새 컨트랙트 생성이다
 *   2. 빈 슬롯 최초 기록(20,000)과 수정(2,900)의 가스 차이
 *   3. require 실패는 상태를 되돌리지만 가스는 소모한다
 *   4. event 의 indexed 는 topics, 나머지는 data 로 갈린다
 *   5. private 은 접근 제한자일 뿐 비밀이 아니다   <- 핵심
 *
 * 실행: https://remix.ethereum.org  (ENVIRONMENT = Remix VM)
 */
contract Claim {

    // ----- 상태 변수. 선언 순서가 그대로 스토리지 슬롯 번호가 된다 -----

    address public  insurer;      // slot 0
    uint256 public  totalPaid;    // slot 1
    uint256 private secret = 777; // slot 2  <- private 인데도 읽힌다
    mapping(address => uint256) public claims;  // slot 3 (값은 해시 위치에)

    // ----- 이벤트. 컨트랙트는 못 읽고, 바깥(인덱서)에서만 읽는다 -----

    event Claimed(address indexed who, uint256 amount);

    // ----- 제어자. 중복되는 권한 검사를 묶는다 -----

    modifier onlyInsurer() {
        require(msg.sender == insurer, "not insurer");
        _;  // 여기에 함수 본문이 끼워진다
    }

    /** 배포될 때 딱 한 번 실행된다. 배포한 사람이 보험사가 된다. */
    constructor() {
        insurer = msg.sender;
    }

    /** 상태를 바꾼다 -> 트랜잭션 필요, 가스 소모. Remix 에서 주황 버튼. */
    function pay(address to, uint256 amount) external onlyInsurer {
        claims[to] += amount;
        totalPaid  += amount;
        emit Claimed(to, amount);
    }

    /** 읽기만 한다 -> 밖에서 호출하면 가스 0. Remix 에서 파란 버튼. */
    function paidOf(address who) external view returns (uint256) {
        return claims[who];
    }
}
