// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * 교재 2강 "Solidity 예제 - Coin 컨트랙트" 를 그대로 옮긴 것.
 *
 *   컨트랙트 생성자가 관리하는 포인트 시스템 컨트랙트로, 포인트 시스템 고유의
 *   주소공간(address space)을 가지며 각 주소의 포인트 잔고를 기록한다.
 *   컨트랙트 생성자는 사용자 주소에 포인트를 부여할 수 있고(mint),
 *   사용자는 다른 사용자에게 포인트를 전송할 수 있다(send).
 *
 * 교재는 Solidity 0.5.6 기준이다. 아래 세 군데만 현행(0.8.x) 문법으로 바꿨고
 * 로직은 한 줄도 바꾸지 않았다.
 *
 *   1. constructor() public  →  constructor()
 *      0.7 부터 생성자에 가시성 지정자를 붙이지 않는다.
 *
 *   2. 오버플로 검사
 *      0.8 부터 산술 오버플로가 자동으로 revert 된다. 교재 시절(0.5)에는
 *      SafeMath 라이브러리를 따로 썼다. require(amount < 1e60) 은
 *      교재 원문 그대로 남겨 두었다 — 지금은 없어도 되지만 왜 있었는지가 교재의 설명이다.
 *
 *   3. 에러 메시지
 *      교재의 require(msg.sender == minter) 에 메시지를 붙였다.
 *      붙이는 쪽이 디버깅에 유리하고, 가스는 배포 시에만 조금 더 든다.
 */
contract Coin {

    // ---------------------------------------------------------------- 상태

    /// 컨트랙트를 배포한 사람. 이 주소만 mint 할 수 있다.
    /// public 키워드가 붙으면 Solidity 가 같은 이름의 조회 함수를 자동 생성한다.
    address public minter;

    /// 주소 → 잔고. 초기화하지 않은 값은 전부 0 이다.
    /// 타 언어의 해시테이블과 비슷하지만 "키 목록"을 보관하지 않아 순회가 불가능하다.
    mapping(address => uint256) public balances;

    // --------------------------------------------------------------- 이벤트

    /// 클라이언트(SDK)가 listening 할 수 있는 데이터.
    /// 컨트랙트 자신은 이 로그를 읽을 수 없다 — 바깥에서 읽으라고 있는 것이다.
    ///
    /// 교재 원문은 event Sent(address from, address to, uint amount) 이다.
    /// from/to 에 indexed 를 붙이면 노드가 필터링해 주므로 조회가 가능해진다.
    /// (indexed 는 최대 3개까지만 붙일 수 있다.)
    event Sent(address indexed from, address indexed to, uint256 amount);

    // ------------------------------------------------------------- 생성자

    /// 배포될 때 딱 한 번 실행된다.
    /// msg.sender 는 "이 함수를 실행한 사람" — 배포 트랜잭션을 보낸 주소다.
    constructor() {
        minter = msg.sender;
    }

    // --------------------------------------------------------------- 함수

    /// receiver 주소에 amount 만큼의 새로운 Coin 을 부여한다.
    ///
    /// require 는 조건이 거짓이면 그 자리에서 전부 되돌린다(revert).
    /// 단, 되돌아가도 그때까지 쓴 가스는 돌려주지 않는다.
    function mint(address receiver, uint256 amount) public {
        require(msg.sender == minter, "Only minter can mint.");
        require(amount < 1e60, "Amount too large.");
        balances[receiver] += amount;
    }

    /// msg.sender 가 receiver 에게 amount 만큼 Coin 을 전송한다.
    function send(address receiver, uint256 amount) public {
        require(balances[msg.sender] >= amount, "Insufficient balance.");
        balances[msg.sender] -= amount;
        balances[receiver] += amount;
        emit Sent(msg.sender, receiver, amount);
    }
}
