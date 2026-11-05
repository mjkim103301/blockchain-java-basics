import java.security.PublicKey;

/**
 * 전자서명 실습 1~6 을 순서대로 실행한다.
 *
 *   1. 공개키·개인키 쌍 생성
 *   2. 임의의 거래 데이터 생성
 *   3. 개인키로 거래 데이터 서명
 *   4. 공개키로 서명 검증
 *   5. 거래 데이터 변경 후 서명 검증 실패 확인
 *   6. 이더리움 트랜잭션의 nonce, gas, signature 필드 조사
 *
 * 실행: bash run.sh SignatureMain
 */
public class SignatureMain {

    public static void main(String[] args) {
        Wallet alice = step1();
        Transaction tx = step2(alice);
        byte[] signature = step3(alice, tx);
        step4(alice, tx, signature);
        step5(alice, tx, signature);
        step6();
        bonusBankVsBlockchain();

        System.out.println();
        System.out.println("=".repeat(88));
        System.out.println("  실습 끝. 흐름도는 docs/transaction-flow.md,");
        System.out.println("           토론 질문 정리는 docs/discussion.md 를 볼 것.");
        System.out.println("=".repeat(88));
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("=".repeat(88));
        System.out.println("  " + title);
        System.out.println("=".repeat(88));
    }

    private static String brief(byte[] bytes) {
        String hex = Hashes.toHex(bytes);
        if (hex.length() <= 24) {
            return hex;
        }
        return hex.substring(0, 16) + "..." + hex.substring(hex.length() - 8)
                + "  (" + bytes.length + "바이트)";
    }

    // --------------------------------------------------- 실습 1

    private static Wallet step1() {
        header("실습 1 — 공개키·개인키 쌍 생성");

        Wallet wallet = Wallet.createEc();

        System.out.println();
        System.out.println("  알고리즘 : " + wallet.algorithm());
        System.out.println("  서명 방식 : " + wallet.signatureAlgorithm());
        System.out.println();
        System.out.println("  개인키 : " + brief(wallet.privateKey().getEncoded()));
        System.out.println("           ^ 절대 밖으로 나가면 안 되는 값");
        System.out.println("  공개키 : " + brief(wallet.publicKey().getEncoded()));
        System.out.println("           ^ 아무에게나 보여 줘도 되는 값");
        System.out.println("  주소   : " + wallet.address());
        System.out.println("           ^ 공개키를 해시해서 뒤 20바이트만 쓴 것");

        System.out.println();
        System.out.println("  ※ 개인키 -> 공개키 -> 주소 는 한 방향으로만 간다.");
        System.out.println("     주소를 봐도 공개키를 모르고, 공개키를 봐도 개인키를 모른다.");
        System.out.println();
        System.out.println("  ※ 개인키는 사실상 그냥 256비트 난수다. 만드는 데 계산이 필요 없다.");
        System.out.println("     비밀이 지켜지는 건 어려워서가 아니라 '경우의 수가 커서' 다.");

        System.out.println();
        System.out.println("  [곡선에 대하여]");
        System.out.println("    이더리움·비트코인은 secp256k1 을 쓴다.");
        System.out.println("    그런데 JDK 16 에서 SunEC 가 이 곡선을 제거했다.");
        System.out.println("    그래서 여기서는 JDK 표준인 " + Wallet.CURVE + " (NIST P-256) 로 실습한다.");
        System.out.println("    둘 다 256비트 타원곡선 ECDSA 다 — 곡선 상수만 다르다.");

        return wallet;
    }

    // --------------------------------------------------- 실습 2

    private static Transaction step2(Wallet alice) {
        header("실습 2 — 거래 데이터 생성");

        Transaction tx = new Transaction(
                alice.address(),
                "0xac03bb73b6a9e108530aff4df5077c2b3d481e5a",
                1_000_000L,
                0L);

        System.out.println();
        System.out.println("  거래   : " + tx.describe());
        System.out.println("  해시   : " + Hashes.brief(tx.hash()));
        System.out.println();
        System.out.println("  서명 대상 바이트열 (" + tx.payload().length + "바이트):");
        System.out.println("    " + readable(tx.payload()));

        System.out.println();
        System.out.println("  ※ 서명은 객체가 아니라 '바이트열' 에 한다.");
        System.out.println("     그래서 객체를 바이트로 바꾸는 규칙이 보안의 일부가 된다.");
        System.out.println("     구분자 없이 이어 붙이면 서로 다른 거래가 같은 바이트열이 될 수 있다.");
        System.out.println("     (이더리움은 RLP 인코딩으로 같은 문제를 해결한다)");

        return tx;
    }

    /** 제어문자를 눈에 보이게 바꾼다. */
    private static String readable(byte[] payload) {
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\u001F", " | ");
    }

    // --------------------------------------------------- 실습 3

    private static byte[] step3(Wallet alice, Transaction tx) {
        header("실습 3 — 개인키로 서명");

        byte[] signature = alice.sign(tx.payload());

        System.out.println();
        System.out.println("  서명 : " + brief(signature));

        System.out.println();
        System.out.println("  [같은 데이터를 다시 서명하면?]");
        byte[] again = alice.sign(tx.payload());
        System.out.println("    1회차 : " + Hashes.brief(Hashes.toHex(signature)));
        System.out.println("    2회차 : " + Hashes.brief(Hashes.toHex(again)));
        System.out.println("    같은가 : " + java.util.Arrays.equals(signature, again));

        System.out.println();
        System.out.println("  ※ 다르다. ECDSA 는 서명할 때마다 난수 k 를 새로 뽑기 때문이다.");
        System.out.println("     둘 다 유효하다 — 검증해 보면 둘 다 통과한다:");
        System.out.println("       1회차 검증 : " + alice.verify(tx.payload(), signature));
        System.out.println("       2회차 검증 : " + alice.verify(tx.payload(), again));
        System.out.println();
        System.out.println("     이 난수가 약하면 개인키가 통째로 샌다. 실제로 2010년 PS3 가");
        System.out.println("     k 를 고정값으로 써서 서명키가 털린 사건이 있다.");
        System.out.println("     그래서 이더리움 지갑은 RFC 6979 로 k 를 데이터에서 결정론적으로 뽑는다.");

        return signature;
    }

    // --------------------------------------------------- 실습 4

    private static void step4(Wallet alice, Transaction tx, byte[] signature) {
        header("실습 4 — 공개키로 검증");

        PublicKey pub = alice.publicKey();
        boolean ok = Wallet.verify(pub, tx.payload(), signature, alice.signatureAlgorithm());

        System.out.println();
        System.out.println("  검증에 쓴 것 : 공개키 + 거래 데이터 + 서명");
        System.out.println("  쓰지 않은 것 : 개인키");
        System.out.println();
        System.out.println("  결과 : " + (ok ? "유효 O" : "무효 X"));

        System.out.println();
        System.out.println("  [남의 공개키로 검증하면?]");
        Wallet mallory = Wallet.createEc();
        boolean wrongKey = Wallet.verify(mallory.publicKey(), tx.payload(), signature,
                alice.signatureAlgorithm());
        System.out.println("    말로리 공개키로 검증 : " + (wrongKey ? "유효 O" : "무효 X"));

        System.out.println();
        System.out.println("  ※ 개인키 없이 검증이 된다는 게 핵심이다.");
        System.out.println("     그래서 전 세계 노드가 각자 알아서 검증할 수 있다.");
        System.out.println("     검증자에게 비밀을 나눠 줄 필요가 없다.");
    }

    // --------------------------------------------------- 실습 5

    private static void step5(Wallet alice, Transaction tx, byte[] signature) {
        header("실습 5 — 데이터를 바꾸면 검증이 실패하는가");

        System.out.println();
        System.out.println("  [변조 전]");
        System.out.println("    거래   : " + tx.describe());
        System.out.println("    검증   : " + verdict(alice.verify(tx.payload(), signature)));

        System.out.println();
        System.out.println("  >>> 서명은 그대로 둔 채 금액만 고친다");
        System.out.println("      1,000,000원  ->  999,000,000원");
        long original = tx.amount();
        tx.tamperAmount(999_000_000L);

        System.out.println();
        System.out.println("  [변조 후]");
        System.out.println("    거래   : " + tx.describe());
        System.out.println("    검증   : " + verdict(alice.verify(tx.payload(), signature)));

        System.out.println();
        System.out.println("  ※ 서명은 거래 내용 전체를 해싱해서 만든다.");
        System.out.println("     한 글자만 바뀌어도 해시가 완전히 달라지고(눈사태 효과),");
        System.out.println("     달라진 해시에 대해서는 그 서명이 성립하지 않는다.");

        // 받는 사람 바꿔치기 — 중간자 공격 흉내
        tx.tamperAmount(original);
        System.out.println();
        System.out.println("  [받는 주소를 바꿔치기하면?]");
        System.out.println("    금액 복구 후 검증 : " + verdict(alice.verify(tx.payload(), signature)));
        tx.tamperTo("0xBADBADBADBADBADBADBADBADBADBADBADBADBAD0");
        System.out.println("    받는 주소 변조 후 : " + verdict(alice.verify(tx.payload(), signature)));

        System.out.println();
        System.out.println("  ※ payload() 에 들어간 필드는 전부 서명으로 보호된다.");
        System.out.println("     거꾸로 말하면 payload() 에 빠진 필드는 보호되지 않는다.");
        System.out.println("     '무엇을 서명 대상에 넣을 것인가' 가 곧 '무엇을 지킬 것인가' 다.");
    }

    private static String verdict(boolean ok) {
        return ok ? "유효 O" : "무효 X  <- 변조 탐지";
    }

    // --------------------------------------------------- 실습 6

    private static void step6() {
        header("실습 6 — 이더리움 트랜잭션 필드 (Type 2 / EIP-1559)");

        System.out.println();
        System.out.printf("  %-22s %-10s %-9s %s%n", "필드", "단위", "전송됨", "역할");
        System.out.println("  " + "-".repeat(84));
        for (EthTxSpec.Field f : EthTxSpec.fields()) {
            System.out.printf("  %-22s %-10s %-9s %s%n",
                    f.name(), f.unit(), f.onWire() ? "O" : "X  <-", f.role());
        }

        System.out.println();
        System.out.println("  ※ from 만 '전송됨 X' 다. 실제 네트워크에 나가는 바이트에는 from 이 없다.");
        System.out.println("     노드가 서명에서 공개키를 역산(ecrecover)하고, 해시해서 주소를 얻는다.");
        System.out.println("     그래서 남의 주소를 from 으로 '주장' 하는 사기가 원천 불가능하다.");
        System.out.println("     실습 1 에서 만든 '개인키 -> 공개키 -> 주소' 사슬이 이걸 가능하게 한다.");

        // ---- 가스 3형제
        System.out.println();
        System.out.println("  [헷갈리는 가스 숫자 세 개 — 단위를 보면 구분된다]");
        System.out.println();
        System.out.printf("  %-22s %-12s %-28s %s%n", "필드", "단위", "정체", "남으면");
        System.out.println("  " + "-".repeat(84));
        for (EthTxSpec.GasField g : EthTxSpec.gasFields()) {
            System.out.printf("  %-22s %-12s %-28s %s%n", g.name(), g.unit(), g.kind(), g.leftover());
        }

        System.out.println();
        System.out.println("  ※ gasLimit 만 'gas' 단위고 나머지는 'gwei/gas' 단위다.");
        System.out.println("     즉 가스비는 하나의 금액이 아니라 [수량 x 단가] 다.");

        // ---- 실제 계산
        long baseFee = 190, priority = 10, maxFee = 300, gasUsed = 21_000;
        long[] fee = EthTxSpec.feeBreakdown(baseFee, priority, maxFee, gasUsed);

        System.out.println();
        System.out.println("  [가스비 계산 — ethereum.org 문서의 예시 숫자]");
        System.out.println();
        System.out.printf("    baseFee %d + 팁 %d = 단가 %d gwei%n", baseFee, priority, baseFee + priority);
        System.out.printf("    단가 %d x 가스 %,d = %,d gwei%n", baseFee + priority, gasUsed, fee[2]);
        System.out.println();
        System.out.printf("      소각 (아무도 못 가져감) : %,10d gwei  = %.6f ETH%n", fee[0], fee[0] / 1e9);
        System.out.printf("      검증자 팁               : %,10d gwei  = %.6f ETH%n", fee[1], fee[1] / 1e9);
        System.out.printf("      총 가스비               : %,10d gwei  = %.6f ETH%n", fee[2], fee[2] / 1e9);

        System.out.println();
        System.out.println("  ※ baseFee 는 프로토콜이 블록마다 자동 산정하고 전액 소각한다.");
        System.out.println("     검증자가 가져가는 건 팁뿐이다. 소각하는 이유는 ETH 가격이 아니라");
        System.out.println("     '검증자가 블록을 일부러 채워 수수료를 올리는' 조작을 막기 위해서다.");

        // ---- 상한 미달 케이스
        long[] tooLow = EthTxSpec.feeBreakdown(190, 10, 150, gasUsed);
        System.out.println();
        System.out.println("  [maxFeePerGas 가 baseFee 보다 낮으면?]");
        System.out.printf("    maxFee=150 < baseFee=190  ->  총 가스비 %,d gwei (체결 자체가 안 됨)%n", tooLow[2]);

        // ---- 타입
        System.out.println();
        System.out.println("  [트랜잭션 타입 — 맨 앞 1바이트]");
        System.out.println();
        System.out.printf("    %-5s %-12s %s%n", "타입", "EIP", "무엇이 달라졌나");
        System.out.println("    " + "-".repeat(80));
        for (EthTxSpec.TxType t : EthTxSpec.types()) {
            System.out.printf("    %-5d %-12s %s%n", t.id(), t.eip(), t.what());
        }

        System.out.println();
        System.out.println("  [nonce 가 하는 일]");
        System.out.println("    서명된 트랜잭션은 공개 네트워크에 떠다니므로 '복사' 가 가능하다.");
        System.out.println("    nonce 가 없으면 그 바이트열을 주워서 100번 다시 뿌리면 100번 집행된다.");
        System.out.println("    계정마다 보낸 횟수를 세고 그 번호를 박으면 재사용이 막힌다.");
        System.out.println("    동시에 '순서' 도 강제된다 — nonce 2 가 막히면 3,4 는 실행될 수 없다.");
        System.out.println("    백엔드로 치면 [멱등키 + 시퀀스 번호] 를 정수 하나로 합친 것이다.");
    }

    // --------------------------------------------------- 보너스: 토론 질문

    private static void bonusBankVsBlockchain() {
        header("보너스 — 은행 전자서명 vs 블록체인 서명 (토론 질문)");

        System.out.println();
        System.out.println("  같은 '전자서명' 이라도 쓰는 알고리즘부터 다르다. 직접 만들어 비교해 보자.");
        System.out.println();

        Wallet bank = Wallet.createRsa();
        Wallet chain = Wallet.createEc();

        byte[] data = "계좌이체 1,000,000원".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bankSig = bank.sign(data);
        byte[] chainSig = chain.sign(data);

        System.out.printf("  %-14s %-16s %-12s %-12s %s%n",
                "", "알고리즘", "공개키", "서명", "검증");
        System.out.println("  " + "-".repeat(84));
        System.out.printf("  %-14s %-16s %-12s %-12s %s%n",
                "은행(공인인증서)", bank.algorithm(),
                bank.publicKey().getEncoded().length + "바이트",
                bankSig.length + "바이트",
                bank.verify(data, bankSig) ? "유효 O" : "무효 X");
        System.out.printf("  %-14s %-16s %-12s %-12s %s%n",
                "블록체인", chain.algorithm(),
                chain.publicKey().getEncoded().length + "바이트",
                chainSig.length + "바이트",
                chain.verify(data, chainSig) ? "유효 O" : "무효 X");

        System.out.println();
        System.out.println("  ※ 같은 보안 수준에서 EC 가 훨씬 작다. 블록 공간이 돈인 환경에서");
        System.out.println("     이 크기 차이는 그대로 비용 차이가 된다.");

        System.out.println();
        System.out.println("  [그런데 진짜 차이는 크기가 아니라 '신뢰의 뿌리' 다]");
        System.out.println();
        System.out.println("    은행   : 이 공개키가 김민지의 것임을 '인증기관(CA)' 이 보증한다.");
        System.out.println("             CA 를 믿기에 서명을 믿는다. 키를 잃어버리면 재발급받는다.");
        System.out.println("             분쟁이 생기면 법원과 은행이 되돌릴 수 있다.");
        System.out.println();
        System.out.println("    블록체인 : 보증하는 기관이 없다. 공개키 그 자체가 신원이다.");
        System.out.println("             '누구의 것인가' 를 아무도 모르고, 알 필요도 없다.");
        System.out.println("             키를 잃으면 재발급이 없다. 자산도 같이 사라진다.");
        System.out.println("             한번 확정된 거래는 누구도 되돌릴 수 없다.");

        System.out.println();
        System.out.println("  자세한 정리는 docs/discussion.md 에 있다.");
    }
}
