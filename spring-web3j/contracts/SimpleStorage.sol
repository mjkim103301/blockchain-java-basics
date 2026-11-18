// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * 교재 2강 "Contract = Code + Data" 의 예제.
 *
 *   set / get 은 함수(코드), storedData 는 상태(데이터)다.
 *   컨트랙트는 결국 이 둘의 묶음이다.
 *
 * 이 컨트랙트가 중요한 이유는 "상태를 바꾸는 함수"와 "바꾸지 않는 함수"가
 * 한 쌍으로 들어 있어서다.
 *
 *   set(x)  상태 변경  →  트랜잭션 필요. 가스 소모. 결과는 영수증으로 확인
 *   get()   읽기만     →  트랜잭션 불필요. 노드에서 바로 실행. 가스 0
 *
 * 교재 표현으로는 전자가 mutation, 후자가 constant(call) 이다.
 */
contract SimpleStorage {

    uint256 private storedData;

    function set(uint256 x) public {
        storedData = x;
    }

    /// view 는 "상태를 읽기만 한다"는 선언이다.
    /// 밖에서 직접 호출하면 트랜잭션이 아니라 eth_call 로 나가므로 가스가 들지 않는다.
    function get() public view returns (uint256) {
        return storedData;
    }
}
