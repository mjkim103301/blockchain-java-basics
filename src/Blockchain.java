import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 블록을 이어 붙인 체인.
 *
 * 검증 로직이 이 클래스의 전부다. 두 가지만 본다.
 *
 *   ① 연결 검사 — 각 블록의 previousHash 가 앞 블록의 실제 해시와 같은가?
 *   ② 작업 검사 — 해시가 난이도 조건을 만족하는가? (PoW 를 쓸 때)
 *
 * ①이 "데이터 무결성"의 실체다. 이 한 줄 비교로 전체 역사를 검증한다.
 */
public class Blockchain {

    private final List<Block> blocks = new ArrayList<>();
    private final int difficulty;

    public Blockchain() {
        this(0); // 난이도 0 = 작업증명 없음
    }

    public Blockchain(int difficulty) {
        this.difficulty = difficulty;

        Block genesis = Block.genesis();
        if (difficulty > 0) {
            genesis.mine(difficulty);
        }
        blocks.add(genesis);
    }

    /** 새 블록을 만들어 끝에 붙인다. 채굴에 걸린 시도 횟수를 돌려준다. */
    public long add(List<String> transactions) {
        Block last = last();
        Block block = new Block(blocks.size(), last.hash(), transactions);

        long attempts = 0;
        if (difficulty > 0) {
            attempts = block.mine(difficulty);
        }

        blocks.add(block);
        return attempts;
    }

    // ------------------------------------------------------------ 검증

    /** 검증 결과. 어디가 왜 깨졌는지까지 담는다. */
    public record Validation(boolean valid, int brokenAt, String reason) {

        public static Validation ok() {
            return new Validation(true, -1, "정상");
        }

        public static Validation broken(int index, String reason) {
            return new Validation(false, index, reason);
        }
    }

    /**
     * 체인 전체를 앞에서부터 검증한다.
     *
     * 블록 하나를 고치면 그 블록의 해시가 달라지고,
     * 다음 블록이 들고 있는 previousHash 와 어긋나면서 여기서 걸린다.
     */
    public Validation validate() {
        for (int i = 1; i < blocks.size(); i++) {
            Block current = blocks.get(i);
            Block previous = blocks.get(i - 1);

            // ① 연결 검사 — 가장 중요한 한 줄
            if (!current.getPreviousHash().equals(previous.hash())) {
                return Validation.broken(i, String.format(
                        "블록 %d 의 previousHash 가 블록 %d 의 실제 해시와 다릅니다%n"
                                + "       기대: %s%n"
                                + "       실제: %s",
                        i, i - 1,
                        Hashes.brief(current.getPreviousHash()),
                        Hashes.brief(previous.hash())));
            }

            // ② 작업 검사
            if (difficulty > 0 && !current.hash().startsWith("0".repeat(difficulty))) {
                return Validation.broken(i, String.format(
                        "블록 %d 가 난이도 조건(0 %d개)을 만족하지 않습니다: %s",
                        i, difficulty, Hashes.brief(current.hash())));
            }
        }
        return Validation.ok();
    }

    // ------------------------------------------------------------ 조회

    public Block get(int index) {
        return blocks.get(index);
    }

    public Block last() {
        return blocks.get(blocks.size() - 1);
    }

    public int size() {
        return blocks.size();
    }

    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public int getDifficulty() {
        return difficulty;
    }

    /** 체인 전체를 표로 찍는다. */
    public void print() {
        System.out.printf("  %-3s %-22s %-22s %-10s %s%n",
                "#", "previousHash", "hash", "nonce", "거래");
        System.out.println("  " + "-".repeat(84));

        for (Block b : blocks) {
            System.out.printf("  %-3d %-22s %-22s %-10d %s%n",
                    b.getIndex(),
                    Hashes.brief(b.getPreviousHash()),
                    Hashes.brief(b.hash()),
                    b.getNonce(),
                    b.getTransactions());
        }
    }
}
