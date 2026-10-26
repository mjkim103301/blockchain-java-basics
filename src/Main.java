import java.util.List;

/**
 * 과제 실습 1~5번을 순서대로 실행한다.
 *
 *   1. 서로 다른 문자열 2개 준비
 *   2. SHA-256 해시 계산
 *   3. 한 글자 변경 후 해시 비교
 *   4. 이전 해시를 포함하는 블록 객체 구성
 *   5. 데이터 변경 시 연결 검증 실패 확인
 *
 * 실행: bash run.sh
 */
public class Main {

    public static void main(String[] args) {
        step1and2();
        step3();
        step4();
        step5();
        bonusProofOfWork();

        System.out.println();
        System.out.println("=".repeat(88));
        System.out.println("  실습 끝. 정리는 README.md 의 비교표를 볼 것.");
        System.out.println("=".repeat(88));
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("=".repeat(88));
        System.out.println("  " + title);
        System.out.println("=".repeat(88));
    }

    // ------------------------------------------------- 실습 1·2

    private static void step1and2() {
        header("실습 1·2 — 서로 다른 문자열 2개의 SHA-256");

        String docA = "계약번호 2026-0001, 피보험자 홍길동, 보험금 1,000,000원";
        String docB = "계약번호 2026-0002, 피보험자 김철수, 보험금 5,000,000원";

        System.out.println("\n  [문서 A]");
        System.out.println("  원문 : " + docA);
        System.out.println("  해시 : " + Hashes.sha256(docA));

        System.out.println("\n  [문서 B]");
        System.out.println("  원문 : " + docB);
        System.out.println("  해시 : " + Hashes.sha256(docB));

        System.out.println("\n  ※ 원문 길이가 달라도 해시는 항상 64자(256비트)로 고정된다.");
        System.out.println("     1GB 파일도 64자가 나온다 — 그래서 '지문'이라고 부른다.");
    }

    // --------------------------------------------------- 실습 3

    private static void step3() {
        header("실습 3 — 한 글자만 바꾸면? (눈사태 효과)");

        String original = "보험금 1,000,000원";
        String modified = "보험금 1,000,001원"; // 마지막 0 → 1, 딱 한 글자

        String hashA = Hashes.sha256(original);
        String hashB = Hashes.sha256(modified);

        System.out.println("\n  원본  : " + original);
        System.out.println("  해시  : " + hashA);
        System.out.println();
        System.out.println("  변경  : " + modified + "   ← 한 글자만 다름");
        System.out.println("  해시  : " + hashB);

        int diffBits = Hashes.differingBits(hashA, hashB);
        double ratio = diffBits * 100.0 / 256;

        System.out.println();
        System.out.printf("  다른 비트 수 : %d / 256  (%.1f%%)%n", diffBits, ratio);
        System.out.println("  첫 글자 같나 : " + (hashA.charAt(0) == hashB.charAt(0)));

        System.out.println();
        System.out.println("  ※ 절반쯤(약 50%) 비트가 뒤집히는 게 정상이다. 이걸 '눈사태 효과'라 한다.");
        System.out.println("     '비슷한 입력 → 비슷한 출력' 이면 원문을 역추적당한다.");
        System.out.println("     아주 조금만 바꿔도 완전히 다른 값이 나와야 안전하다.");

        // 같은 입력은 언제나 같은 출력 (결정성)
        System.out.println();
        System.out.println("  [결정성 확인] 같은 입력을 3번 해시하면:");
        for (int i = 1; i <= 3; i++) {
            System.out.println("    " + i + "회차 : " + Hashes.brief(Hashes.sha256(original)));
        }
        System.out.println("  ※ 항상 같다. 그래서 누가 어디서 계산해도 검증이 가능하다.");
    }

    // --------------------------------------------------- 실습 4

    private static void step4() {
        header("실습 4 — 이전 해시를 포함하는 블록 연결");

        Blockchain chain = new Blockchain();
        chain.add(List.of("홍길동 → 김철수 100만원", "이영희 → 박민수 50만원"));
        chain.add(List.of("최수진 → 정대현 30만원"));
        chain.add(List.of("강지우 → 윤서준 70만원"));

        System.out.println();
        chain.print();

        System.out.println();
        System.out.println("  ※ 각 블록의 previousHash 가 바로 위 블록의 hash 와 같다.");
        System.out.println("     이 고리가 '체인' 이다.");

        System.out.println("\n  [연결 확인]");
        for (int i = 1; i < chain.size(); i++) {
            Block cur = chain.get(i);
            Block prev = chain.get(i - 1);
            boolean linked = cur.getPreviousHash().equals(prev.hash());
            System.out.printf("    %s 블록%d.previousHash == 블록%d.hash%n",
                    linked ? "✓" : "✗", i, i - 1);
        }

        Blockchain.Validation result = chain.validate();
        System.out.println("\n  검증 결과 : " + (result.valid() ? "정상 ✓" : "깨짐 ✗"));
    }

    // --------------------------------------------------- 실습 5

    private static void step5() {
        header("실습 5 — 데이터를 변조하면 검증이 실패하는가?");

        Blockchain chain = new Blockchain();
        chain.add(List.of("홍길동 → 김철수 100만원"));
        chain.add(List.of("최수진 → 정대현 30만원"));
        chain.add(List.of("강지우 → 윤서준 70만원"));

        System.out.println("\n  [변조 전]");
        chain.print();
        System.out.println("\n  검증 : " + describe(chain.validate()));

        // 해시를 미리 기록해 둔다 — 변조 후 비교하려고
        String[] before = new String[chain.size()];
        for (int i = 0; i < chain.size(); i++) {
            before[i] = chain.get(i).hash();
        }

        System.out.println("\n  >>> 블록 1 의 거래를 몰래 고친다");
        System.out.println("      \"홍길동 → 김철수 100만원\"  →  \"홍길동 → 김철수 9999만원\"");
        chain.get(1).tamper(0, "홍길동 → 김철수 9999만원");

        System.out.println("\n  [변조 후]");
        chain.print();

        System.out.println("\n  [해시가 어떻게 달라졌나]");
        System.out.printf("    %-5s %-22s %-22s %s%n", "블록", "변조 전", "변조 후", "");
        System.out.println("    " + "-".repeat(62));
        for (int i = 0; i < chain.size(); i++) {
            String now = chain.get(i).hash();
            boolean changed = !before[i].equals(now);
            System.out.printf("    %-5d %-22s %-22s %s%n",
                    i, Hashes.brief(before[i]), Hashes.brief(now),
                    changed ? "← 바뀜" : "");
        }

        System.out.println();
        System.out.println("  ※ 고친 건 블록 1 하나인데 블록 1 의 해시만 바뀌었다.");
        System.out.println("     블록 2, 3 은 '이미 저장된' previousHash 를 그대로 들고 있어서");
        System.out.println("     블록 1 의 새 해시와 어긋난다 — 여기서 걸린다.");

        Blockchain.Validation result = chain.validate();
        System.out.println("\n  검증 : " + describe(result));

        System.out.println("\n  [그럼 뒤 블록들도 같이 고치면 되지 않나?]");
        System.out.println("    이론상 가능하다. 하지만 실제 블록체인에서는:");
        System.out.println("      1. 뒤 블록 전부를 다시 채굴해야 한다 (작업증명)");
        System.out.println("      2. 그동안 정직한 노드들은 계속 새 블록을 쌓는다");
        System.out.println("      3. 전체 연산력의 51% 이상이 없으면 따라잡지 못한다");
        System.out.println("    → 다음 보너스 실습에서 '다시 채굴' 비용을 직접 확인한다.");
    }

    private static String describe(Blockchain.Validation v) {
        if (v.valid()) {
            return "정상 ✓";
        }
        return "실패 ✗ (블록 " + v.brokenAt() + ")\n       " + v.reason();
    }

    // ------------------------------------------------- 보너스

    private static void bonusProofOfWork() {
        header("보너스 — 작업증명: 변조가 왜 '비싼가'");

        System.out.println("\n  해시가 0 으로 N 개 시작할 때까지 nonce 를 바꿔 가며 다시 계산한다.");
        System.out.println("  역산이 불가능하므로 무식하게 다 해보는 수밖에 없다.\n");

        System.out.printf("  %-8s %-14s %-12s %s%n", "난이도", "시도 횟수", "걸린 시간", "찾은 해시");
        System.out.println("  " + "-".repeat(72));

        for (int difficulty = 1; difficulty <= 5; difficulty++) {
            Block block = new Block(1, "0".repeat(64), List.of("홍길동 → 김철수 100만원"));

            long start = System.nanoTime();
            long attempts = block.mine(difficulty);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            System.out.printf("  0 %-6d %,-14d %,-12d %s%n",
                    difficulty, attempts, elapsedMs, Hashes.brief(block.hash()));
        }

        System.out.println();
        System.out.println("  ※ 난이도가 1 오를 때마다 시도 횟수가 약 16배로 뛴다.");
        System.out.println("     (16진수 한 자리가 0 일 확률이 1/16 이므로)");
        System.out.println();
        System.out.println("     비트코인의 실제 난이도는 0 이 19개쯤이다.");
        System.out.println("     블록 하나 변조하려면 그 뒤 블록을 전부 이만큼씩 다시 캐야 한다.");
        System.out.println("     → '불가능' 이 아니라 '수지가 안 맞음' 으로 막는 것이다.");
    }
}
