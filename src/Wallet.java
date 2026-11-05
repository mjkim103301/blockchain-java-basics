import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;

/**
 * 지갑 — 키 쌍 하나와 그것으로 하는 일(서명).
 *
 * 과제 1번 "공개키·개인키 쌍 생성" 에 해당한다.
 *
 * <h3>곡선 선택에 대하여</h3>
 * 이더리움·비트코인은 <b>secp256k1</b> 을 쓴다. 그런데 JDK 16 에서 SunEC 가
 * 이 곡선을 제거했다(JDK 21 에서도 {@code Curve not supported} 로 실패한다).
 * 그래서 여기서는 JDK 표준이 지원하는 <b>secp256r1(= NIST P-256)</b> 로 실습한다.
 *
 * 둘 다 256비트 타원곡선이고 ECDSA 를 쓴다는 점은 같다. 곡선 상수만 다르다.
 * 실제 이더리움 키를 다뤄야 한다면 BouncyCastle 이나 web3j 를 써야 한다.
 */
public final class Wallet {

    /** JDK 표준이 지원하는 256비트 곡선. 이더리움의 secp256k1 과 역할은 같다. */
    public static final String CURVE = "secp256r1";

    private final KeyPair keyPair;
    private final String algorithm;
    private final String signatureAlgorithm;

    private Wallet(KeyPair keyPair, String algorithm, String signatureAlgorithm) {
        this.keyPair = keyPair;
        this.algorithm = algorithm;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /** 타원곡선 지갑. 블록체인이 쓰는 방식이다. */
    public static Wallet createEc() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return new Wallet(generator.generateKeyPair(), "EC/" + CURVE, "SHA256withECDSA");
        } catch (Exception e) {
            throw new IllegalStateException("EC 키 쌍 생성 실패", e);
        }
    }

    /**
     * RSA 지갑. 은행 공인인증서·금융인증서가 쓰는 방식이다.
     *
     * 2048비트로 고정했다. JDK 21 의 기본값은 3072비트지만,
     * 실습의 비교 대상(공인인증서)이 2048비트라 맞췄다.
     */
    public static Wallet createRsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return new Wallet(generator.generateKeyPair(), "RSA/2048", "SHA256withRSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA 키 쌍 생성 실패", e);
        }
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    public String algorithm() {
        return algorithm;
    }

    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * 주소 — 공개키를 해시해서 뒤 20바이트만 쓴다.
     *
     * 이더리움과 같은 방식이되 해시 함수가 다르다.
     * 이더리움은 keccak256 을 쓰는데 JDK 표준에는 없다.
     * (JDK 의 SHA3-256 은 keccak256 과 패딩이 달라서 값이 다르게 나온다.)
     * 여기서는 SHA-256 으로 대체했다 — 구조를 보는 게 목적이므로 문제없다.
     */
    public String address() {
        String hash = Hashes.toHex(sha256(publicKey().getEncoded()));
        return "0x" + hash.substring(hash.length() - 40);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 개인키로 서명한다. 과제 3번.
     *
     * 주의 — ECDSA 는 서명할 때마다 난수 k 를 새로 뽑는다.
     * 그래서 <b>같은 데이터를 두 번 서명하면 서명 바이트가 다르다.</b>
     * 둘 다 유효하다. (이더리움 지갑은 RFC 6979 로 k 를 결정론적으로 만들어
     * 이 난수성을 없앤다 — 난수 생성기가 약하면 개인키가 새기 때문이다.)
     */
    public byte[] sign(byte[] data) {
        try {
            Signature signer = Signature.getInstance(signatureAlgorithm);
            signer.initSign(keyPair.getPrivate());
            signer.update(data);
            return signer.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("서명 실패", e);
        }
    }

    /**
     * 공개키로 검증한다. 과제 4번.
     *
     * 개인키는 쓰지 않는다 — 그래서 누구나 검증할 수 있다.
     * 데이터가 한 바이트라도 다르면 false 가 나온다. 이게 과제 5번이다.
     */
    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature,
                                 String signatureAlgorithm) {
        try {
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (SignatureException e) {
            // 서명 바이트 자체가 깨졌을 때도 여기로 온다. 검증 실패와 같게 취급한다.
            return false;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("검증 실패", e);
        }
    }

    /** 이 지갑의 알고리즘으로 검증한다. */
    public boolean verify(byte[] data, byte[] signature) {
        return verify(publicKey(), data, signature, signatureAlgorithm);
    }
}
