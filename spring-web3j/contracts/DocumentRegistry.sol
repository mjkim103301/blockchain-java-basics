// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * 4주차 실습 — 전자계약 문서 무결성 검증 API 의 온체인 부분.
 *
 * 왜 Claim.sol(3주차 컨트랙트)을 그대로 못 쓰는가
 *   Claim 은 "주소 -> 지급액" 장부다. 문서를 저장할 자리가 없다.
 *   그래서 "3. 3주차 컨트랙트를 배포한다" 는 Claim.sol 로 수행하고
 *   (ClaimRedeployTest 참고), 실제 문서 해시를 다루는 이 컨트랙트는
 *   따로 둔다 — 같은 패턴(require·mapping·event)을 그대로 재사용했다.
 *
 * 원리는 이전 주차들에서 반복해 다룬 "앵커링" 패턴 그대로다.
 *
 *   문서 원본            -----  절대 온체인에 안 올린다. 삭제가 불가능하고
 *   (PDF, 계약서 등)            전부 공개되기 때문이다 (3주차 private 노출 참고)
 *
 *   문서의 SHA-256 해시  -----  이것만 올린다. 32바이트. 원문을 복원할 수 없다
 *
 * 그래서 이 컨트랙트가 저장하는 건 "문서"가 아니라 "문서의 지문 하나"다.
 * 같은 해시를 두 번 등록하면 막는다 — 한 번 찍힌 지문은 다시 찍을 수 없다.
 */
contract DocumentRegistry {

    /// 등록 한 건의 기록. registeredAt 이 0 이면 "등록된 적 없음" 이다.
    /// (1주차 Block, 3주차 Claim 과 같은 규칙 — 0 은 값이 아니라 '빈 칸' 표시다)
    struct Record {
        address registrant;     // 등록한 사람(= 문서를 가진 쪽)
        uint256 registeredAt;   // block.timestamp
        uint256 blockNumber;    // 어느 블록에서 등록됐는지
    }

    /// 문서 해시 -> 등록 기록. slot 0.
    /// key 가 "문서 내용"이 아니라 "문서 내용의 해시"라는 점이 핵심이다.
    mapping(bytes32 => Record) private records;

    /// 클라이언트(= 우리 Spring 서비스)가 구독하는 로그.
    /// 컨트랙트 자신은 이 이벤트를 못 읽는다 — 밖에서만 읽는다.
    event DocumentRegistered(
        bytes32 indexed docHash,
        address indexed registrant,
        uint256 registeredAt
    );

    /**
     * 문서 해시를 등록한다. 상태를 바꾸므로 트랜잭션이 필요하다 (가스 소모).
     *
     * require 가 막는 경우 — 같은 해시가 이미 등록돼 있을 때.
     * "같은 문서를 두 번 등록"이 아니라 "다른 두 문서가 우연히 같은
     * 해시를 가질 확률"은 사실상 0 이므로, 이 체크는 사실상
     * "이 문서는 이미 등록됐다"는 뜻과 같다.
     */
    function register(bytes32 docHash) external {
        require(records[docHash].registeredAt == 0, "Document already registered.");

        records[docHash] = Record({
            registrant: msg.sender,
            registeredAt: block.timestamp,
            blockNumber: block.number
        });

        emit DocumentRegistered(docHash, msg.sender, block.timestamp);
    }

    /**
     * 문서 해시로 등록 여부를 확인한다. 읽기만 하므로 가스가 들지 않는다.
     *
     * exists 가 false 면 두 가지 중 하나다.
     *   ① 애초에 등록한 적 없는 문서다
     *   ② 등록된 문서가 변조돼서 해시가 달라졌다 — 변조 탐지가 여기서 일어난다
     * 이 컨트랙트는 둘을 구분하지 못한다. 구분은 Spring 쪽에서 "원래 가지고
     * 있던 docHash" 와 "지금 계산한 docHash" 를 비교해서 한다.
     */
    function verify(bytes32 docHash)
        external
        view
        returns (bool exists, address registrant, uint256 registeredAt, uint256 blockNumber)
    {
        Record memory r = records[docHash];
        exists = r.registeredAt != 0;
        registrant = r.registrant;
        registeredAt = r.registeredAt;
        blockNumber = r.blockNumber;
    }
}
