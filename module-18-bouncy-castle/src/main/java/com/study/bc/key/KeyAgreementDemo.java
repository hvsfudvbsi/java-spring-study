package com.study.bc.key;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;

import com.study.bc.BcSupport;

/**
 * 密钥协商演示：DH（Diffie-Hellman）与 ECDH（椭圆曲线 DH）。
 *
 * <p>双方各自生成密钥对，交换公钥后各自计算得到**相同的共享秘密**，
 * 该秘密可派生对称密钥。安全性依赖离散对数/椭圆曲线离散对数难题。
 *
 * <p>适用场景：TLS 密钥交换（DHE/ECDHE）、端到端加密（Signal 等）、即时通信；
 * 协商出的共享秘密再经 KDF 派生对称密钥。
 */
public final class KeyAgreementDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private KeyAgreementDemo() {
    }

    static {
        BcSupport.register(); // ECDH 需 BC provider
    }

    /** DH 密钥协商：返回双方共享秘密（长度相同则一致）。 */
    public static byte[] dhAgree(DHParameterSpec spec) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
            kpg.initialize(spec, RANDOM);
            KeyPair alice = kpg.generateKeyPair();
            KeyPair bob = kpg.generateKeyPair();

            KeyAgreement aliceAgree = KeyAgreement.getInstance("DH");
            aliceAgree.init(alice.getPrivate());
            aliceAgree.doPhase(bob.getPublic(), true);
            byte[] aliceSecret = aliceAgree.generateSecret();

            KeyAgreement bobAgree = KeyAgreement.getInstance("DH");
            bobAgree.init(bob.getPrivate());
            bobAgree.doPhase(alice.getPublic(), true);
            byte[] bobSecret = bobAgree.generateSecret();

            return aliceSecret.length == bobSecret.length && java.util.Arrays.equals(aliceSecret, bobSecret)
                    ? aliceSecret : new byte[0];
        } catch (Exception e) {
            throw new IllegalStateException("DH 协商失败", e);
        }
    }

    /** ECDH 密钥协商：返回双方共享秘密。 */
    public static byte[] ecdhAgree() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
            kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), RANDOM);
            KeyPair alice = kpg.generateKeyPair();
            KeyPair bob = kpg.generateKeyPair();

            KeyAgreement aliceAgree = KeyAgreement.getInstance("ECDH", "BC");
            aliceAgree.init(alice.getPrivate());
            aliceAgree.doPhase(bob.getPublic(), true);
            byte[] aliceSecret = aliceAgree.generateSecret();

            KeyAgreement bobAgree = KeyAgreement.getInstance("ECDH", "BC");
            bobAgree.init(bob.getPrivate());
            bobAgree.doPhase(alice.getPublic(), true);
            byte[] bobSecret = bobAgree.generateSecret();

            return aliceSecret.length == bobSecret.length && java.util.Arrays.equals(aliceSecret, bobSecret)
                    ? aliceSecret : new byte[0];
        } catch (Exception e) {
            throw new IllegalStateException("ECDH 协商失败", e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        try {
            // 用 Alice 的 DH 参数生成共享参数（2048 位）
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
            kpg.initialize(2048, RANDOM);
            KeyPair alice = kpg.generateKeyPair();
            DHParameterSpec spec = ((DHPublicKey) alice.getPublic()).getParams();

            byte[] dh = dhAgree(spec);
            System.out.println("DH-2048 共享秘密 (" + dh.length + " 字节): " + HEX.formatHex(dh).substring(0, 48) + "...");
            System.out.println("  双方一致: " + (dh.length > 0));

            byte[] ecdh = ecdhAgree();
            System.out.println("ECDH P-256 共享秘密 (" + ecdh.length + " 字节): " + HEX.formatHex(ecdh).substring(0, 48) + "...");
            System.out.println("  双方一致: " + (ecdh.length > 0));
        } catch (Exception e) {
            throw new IllegalStateException("密钥协商演示失败", e);
        }
        System.out.println();
    }
}
