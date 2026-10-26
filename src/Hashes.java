import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 해시 유틸리티.
 *
 * 자바 표준 라이브러리(java.security.MessageDigest)만 쓴다.
 * 외부 의존성이 없으므로 JDK만 있으면 어디서든 돈다.
 *
 * MessageDigest 는 스레드 안전하지 않다. 그래서 매번 새로 얻는다.
 * 재사용하려면 reset() 을 부르거나 스레드마다 하나씩 둬야 한다.
 */
public final class Hashes {

    private Hashes() {
    }

    /** 문자열 → SHA-256 → 16진수 64자리. */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JDK 가 반드시 제공한다. 여기 올 일은 없다.
            throw new IllegalStateException("SHA-256 을 찾을 수 없습니다", e);
        }
    }

    /** 바이트 배열 → 소문자 16진수 문자열. */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // b 는 부호 있는 byte 라 0xff 로 마스킹해야 음수가 안 된다
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    /**
     * 두 해시가 몇 비트나 다른지 센다.
     *
     * 눈사태 효과(avalanche effect)를 수치로 확인하려고 만들었다.
     * 입력을 한 글자만 바꿔도 출력 비트의 약 절반이 뒤집혀야 좋은 해시다.
     */
    public static int differingBits(String hexA, String hexB) {
        if (hexA.length() != hexB.length()) {
            throw new IllegalArgumentException("길이가 다른 해시는 비교할 수 없습니다");
        }

        int diff = 0;
        for (int i = 0; i < hexA.length(); i++) {
            int a = Character.digit(hexA.charAt(i), 16);
            int b = Character.digit(hexB.charAt(i), 16);
            diff += Integer.bitCount(a ^ b); // XOR 후 1의 개수 = 다른 비트 수
        }
        return diff;
    }

    /** 앞뒤만 보여주는 짧은 표기. 로그 찍을 때 쓴다. */
    public static String brief(String hash) {
        if (hash == null) {
            return "null";
        }
        if (hash.length() <= 20) {
            return hash;
        }
        return hash.substring(0, 12) + "..." + hash.substring(hash.length() - 4);
    }
}
