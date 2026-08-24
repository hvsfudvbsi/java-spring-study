package com.study.bc.asymmetric;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.HexFormat;

import javax.crypto.KeyAgreement;

import com.study.bc.BcSupport;

/**
 * 椭圆曲线密码（ECC）演示：密钥生成、ECDH 密钥协商、ECDSA 签名。
 *
 * <p>说明：ECC 用更短的密钥提供等效安全强度（256 位 ≈ RSA-3072），
 * 是现代 TLS/区块链的主流。演示采用 BC provider 的 secp256r1（P-256）曲线。
 */
public final class EccDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private EccDemo() {
    }

    static {
        BcSupport.register(); // JCE 接口经 "BC" provider 调用
    }

    /** 生成 P-256 曲线密钥对（BC provider）。 */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("ECC 密钥生成失败", e);
        }
    }

    /** ECDH 密钥协商：用自己的私钥 + 对方公钥，得到共享秘密。 */
    public static byte[] ecdh(KeyPair own, java.security.PublicKey peer) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH", "BC");
            agreement.init(own.getPrivate());
            agreement.doPhase(peer, true);
            return agreement.generateSecret();
        } catch (Exception e) {
            throw new IllegalStateException("ECDH 协商失败", e);
        }
    }

    /** ECDSA 签名（SHA-256 哈希）。 */
    public static byte[] sign(KeyPair keyPair, byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
            signature.initSign(keyPair.getPrivate(), RANDOM);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("ECDSA 签名失败", e);
        }
    }

    /** ECDSA 验签。 */
    public static boolean verify(KeyPair keyPair, byte[] data, byte[] sig) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
            signature.initVerify(keyPair.getPublic());
            signature.update(data);
            return signature.verify(sig);
        } catch (Exception e) {
            throw new IllegalStateException("ECDSA 验签失败", e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        KeyPair alice = generateKeyPair();
        KeyPair bob = generateKeyPair();

        System.out.println("ECC P-256 密钥对: Alice/Bob 各生成一对");
        System.out.println("  Alice 公钥: " + HEX.formatHex(alice.getPublic().getEncoded()).substring(0, 48) + "...");
        System.out.println();

        byte[] aliceSecret = ecdh(alice, bob.getPublic());
        byte[] bobSecret = ecdh(bob, alice.getPublic());
        System.out.println("ECDH 协商共享秘密: " + HEX.formatHex(aliceSecret));
        System.out.println("  Alice 与 Bob 一致: " + java.util.Arrays.equals(aliceSecret, bobSecret));
        System.out.println();

        String msg = "椭圆曲线数字签名演示";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(alice, data);
        System.out.println("ECDSA 签名(" + msg + "): " + HEX.formatHex(sig));
        System.out.println("  验签(Alice 公钥): " + verify(alice, data, sig));
        System.out.println("  验签(篡改数据)  : " + verify(alice, (msg + "!").getBytes(StandardCharsets.UTF_8), sig));
        System.out.println();
    }
}
