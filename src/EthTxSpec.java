import java.util.List;

/**
 * 이더리움 트랜잭션의 필드 명세.
 *
 * 과제 6번 "이더리움 트랜잭션의 nonce, gas, signature 필드 조사" 에 해당한다.
 *
 * 코드로 뭔가를 계산하지는 않는다. 조사 결과를 <b>구조화해서 출력</b>하는 것이
 * 목적이다 — 표로 정리해 두면 실제 트랜잭션을 볼 때 바로 대조할 수 있다.
 *
 * 기준은 <b>Type 2 (EIP-1559)</b> 트랜잭션이다. 지금 지갑이 기본으로 쓰는 형식이다.
 * 출처: https://ethereum.org/ko/developers/docs/transactions/
 */
public final class EthTxSpec {

    private EthTxSpec() {
    }

    /** 필드 하나의 명세. */
    public record Field(String name, String unit, String role, String example, boolean onWire) {
    }

    /**
     * 서명 전 트랜잭션 객체의 필드들.
     *
     * {@code onWire} 가 false 인 것은 <b>실제 전송 바이트에 들어가지 않는</b> 필드다.
     * from 하나뿐인데, 이게 이 과제의 핵심이다.
     */
    public static List<Field> fields() {
        return List.of(
                new Field("from", "주소",
                        "보내는 사람. 적어 넣는 게 아니라 서명에서 역산된다",
                        "0xEA674fdD...98ec8", false),
                new Field("to", "주소",
                        "받는 주소. 비우면 '새 컨트랙트 배포' 라는 뜻",
                        "0xac03bb73...81e5a", true),
                new Field("value", "wei",
                        "보낼 ETH. 1 ETH = 10^18 wei. 정수만 쓴다",
                        "1000000000000000000", true),
                new Field("nonce", "정수",
                        "이 계정이 보낸 트랜잭션 일련번호. 0부터 1씩",
                        "0", true),
                new Field("data", "바이트",
                        "컨트랙트 입력. 앞 4바이트가 함수 선택자, 뒤가 인자",
                        "0x (단순 송금은 빔)", true),
                new Field("gasLimit", "gas",
                        "수량 한도. '연산 이만큼까지만 허용한다'",
                        "21000", true),
                new Field("maxFeePerGas", "gwei/gas",
                        "단가 상한. 지불 한도지 지불액이 아니다",
                        "300", true),
                new Field("maxPriorityFeePerGas", "gwei/gas",
                        "검증자에게 주는 팁. 사실상 입찰가",
                        "10", true),
                new Field("signature", "(r,s,v)",
                        "개인키로 만든 ECDSA 서명. 신원 증명이자 위변조 방지",
                        "0x3f1b... / 0x9a02... / 1", true)
        );
    }

    // ------------------------------------------------- 가스 3형제

    /**
     * 이름이 비슷해서 제일 많이 섞이는 세 숫자.
     *
     * 핵심은 <b>단위가 다르다</b>는 것이다.
     * gasLimit 만 gas 단위고, 나머지 둘은 gwei/gas 단위다.
     * 즉 gasLimit 은 '수량', 나머지는 '단가' 다.
     */
    public record GasField(String name, String unit, String kind, String leftover) {
    }

    public static List<GasField> gasFields() {
        return List.of(
                new GasField("gasLimit", "gas",
                        "수량 한도 — 무한루프 방어선", "안 쓴 만큼 안 낸다"),
                new GasField("maxFeePerGas", "gwei/gas",
                        "단가 상한 — 여기까지만 낸다", "차액은 돌려받는다"),
                new GasField("maxPriorityFeePerGas", "gwei/gas",
                        "팁 — 검증자 몫", "그대로 지급된다")
        );
    }

    /**
     * 실제 가스비 계산.
     *
     * <pre>
     *   실제 단가 = min(maxFeePerGas, baseFee + priorityFee)
     *   총 가스비 = 실제 단가 x 가스 사용량
     * </pre>
     *
     * baseFee 는 내가 정하지 않는다. 프로토콜이 블록마다 자동 산정하며
     * <b>전액 소각</b>된다. 검증자가 가져가는 건 팁뿐이다.
     *
     * @return {소각분, 팁, 총액} 단위는 gwei
     */
    public static long[] feeBreakdown(long baseFee, long priorityFee, long maxFee, long gasUsed) {
        if (maxFee < baseFee) {
            // 상한이 의무 지불액에 못 미친다 -> 블록에 들어갈 수 없다
            return new long[]{0, 0, 0};
        }
        long paidTip = Math.min(priorityFee, maxFee - baseFee);
        return new long[]{baseFee * gasUsed, paidTip * gasUsed, (baseFee + paidTip) * gasUsed};
    }

    // ------------------------------------------------- 트랜잭션 타입

    public record TxType(int id, String eip, String what) {
    }

    public static List<TxType> types() {
        return List.of(
                new TxType(0, "-", "레거시. gasPrice 하나만 있었다"),
                new TxType(1, "EIP-2930", "accessList 추가. 건드릴 주소를 미리 신고하면 할인"),
                new TxType(2, "EIP-1559", "현재 표준. baseFee 자동 산정 + 소각, 팁 분리"),
                new TxType(3, "EIP-4844", "블롭 데이터. L2 롤업 전용 칸"),
                new TxType(4, "EIP-7702", "일반 계정이 일시적으로 컨트랙트처럼 동작")
        );
    }
}
