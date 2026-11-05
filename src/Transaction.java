import java.nio.charset.StandardCharsets;

/**
 * 거래 데이터 하나.
 *
 * 과제 2번 "임의의 거래 데이터 생성" 에 해당한다.
 *
 * <h3>왜 필드를 그냥 이어 붙이지 않는가</h3>
 * 서명은 <b>바이트열</b> 에 대고 한다. 그래서 객체를 바이트로 바꾸는 방법
 * ({@link #payload()}) 자체가 보안의 일부가 된다.
 *
 * 만약 구분자 없이 이어 붙이면:
 * <pre>
 *   from="0xAB", to="0xCD",  amount=12   -&gt;  "0xAB0xCD12"
 *   from="0xAB", to="0xCD1", amount=2    -&gt;  "0xAB0xCD12"   &lt;- 같다!
 * </pre>
 * 내용이 다른 두 거래가 같은 바이트열이 되고, 한쪽 서명이 다른 쪽에도 통한다.
 * 그래서 필드 사이에 <b>값에 나올 수 없는 구분자</b>를 넣고 필드 이름까지 적는다.
 * 실제 이더리움은 RLP 라는 인코딩으로 같은 문제를 해결한다.
 */
public class Transaction {

    /** ASCII Unit Separator(0x1F). 주소나 숫자에는 절대 등장하지 않는다. */
    private static final char SEP = '\u001F';

    private String from;
    private String to;
    private long amount;
    private final long nonce;

    public Transaction(String from, String to, long amount, long nonce) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.nonce = nonce;
    }

    /**
     * 서명 대상 바이트열.
     *
     * 이 메서드가 뱉는 값이 바뀌면 서명이 깨진다. 즉 여기 들어간 필드는
     * 전부 서명으로 보호되고, 빠진 필드는 보호되지 않는다.
     */
    public byte[] payload() {
        String canonical = "from=" + from + SEP
                + "to=" + to + SEP
                + "amount=" + amount + SEP
                + "nonce=" + nonce;
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    /** 사람이 읽는 형태. 출력용이지 서명 대상이 아니다. */
    public String describe() {
        return String.format("%s -> %s  %,d원  (nonce=%d)", brief(from), brief(to), amount, nonce);
    }

    /** 이 거래의 해시. 블록에 담길 때 쓰는 식별자다. */
    public String hash() {
        return Hashes.toHex(sha256(payload()));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String brief(String address) {
        if (address == null || address.length() <= 14) {
            return address;
        }
        return address.substring(0, 8) + "..." + address.substring(address.length() - 4);
    }

    // ---------------- 과제 5번용 — 서명한 뒤에 몰래 고치기 ----------------

    /** 금액을 바꾼다. 서명은 그대로 둔 채 데이터만 고치는 공격을 흉내낸다. */
    public void tamperAmount(long newAmount) {
        this.amount = newAmount;
    }

    /** 받는 사람을 바꾼다. */
    public void tamperTo(String newTo) {
        this.to = newTo;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public long amount() {
        return amount;
    }

    public long nonce() {
        return nonce;
    }
}
