import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 블록 하나.
 *
 * 과제 4번 "이전 해시값을 포함하는 블록 데이터 객체" 에 해당한다.
 *
 * 핵심은 hash 를 **저장하지 않고 계산한다**는 점이다.
 *   - 저장해 두면 데이터를 고친 뒤 해시도 같이 고칠 수 있다
 *   - 매번 계산하면 데이터가 바뀌는 순간 해시가 저절로 달라진다
 *
 * 실제 블록체인도 해시를 블록 안에 담지 않는다. 받는 쪽이 직접 계산해서 확인한다.
 */
public class Block {

    private final int index;
    private final long timestamp;
    private final String previousHash;
    private final List<String> transactions;

    /** 작업증명용. 이 값을 바꿔 가며 조건에 맞는 해시를 찾는다. */
    private long nonce;

    public Block(int index, String previousHash, List<String> transactions) {
        this.index = index;
        this.previousHash = previousHash;
        this.transactions = new ArrayList<>(transactions);
        this.timestamp = Instant.now().toEpochMilli();
        this.nonce = 0;
    }

    /** 제네시스 블록. 앞이 없으므로 previousHash 가 0 으로 채워진다. */
    public static Block genesis() {
        return new Block(0, "0".repeat(64), List.of("제네시스 블록"));
    }

    /**
     * 이 블록의 해시를 **지금 계산한다**.
     *
     * 재료에 previousHash 가 들어가는 것이 전부다.
     * 이 한 줄 때문에 앞 블록이 바뀌면 이 블록의 해시도 따라 바뀐다.
     */
    public String hash() {
        String payload = index
                + "|" + timestamp
                + "|" + previousHash
                + "|" + String.join(",", transactions)
                + "|" + nonce;
        return Hashes.sha256(payload);
    }

    /**
     * 작업증명 — 해시가 0 으로 difficulty 개만큼 시작할 때까지 nonce 를 돌린다.
     *
     * 해시는 역산이 불가능하므로 **무식하게 다 해보는 수밖에 없다**.
     * 그래서 '작업(work)' 이고, 그 결과물인 nonce 가 '증명(proof)' 이다.
     */
    public long mine(int difficulty) {
        String target = "0".repeat(difficulty);
        long attempts = 0;

        while (!hash().startsWith(target)) {
            nonce++;
            attempts++;
        }
        return attempts;
    }

    // ---------------------------------------------------------- 변조용

    /**
     * 거래 내용을 바꿔치기한다. **실습 5번을 위해 일부러 열어 둔 뒷문이다.**
     *
     * 실제 블록체인에는 이런 메서드가 없다. 변조해도 검증에서 걸린다는 걸
     * 보여주려고 만든 것이다.
     */
    public void tamper(int txIndex, String newTransaction) {
        transactions.set(txIndex, newTransaction);
    }

    // ---------------------------------------------------------- 조회

    public int getIndex() {
        return index;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public List<String> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public long getNonce() {
        return nonce;
    }

    @Override
    public String toString() {
        return String.format(
                "Block#%d{tx=%s, prev=%s, hash=%s, nonce=%d}",
                index, transactions, Hashes.brief(previousHash), Hashes.brief(hash()), nonce);
    }
}
