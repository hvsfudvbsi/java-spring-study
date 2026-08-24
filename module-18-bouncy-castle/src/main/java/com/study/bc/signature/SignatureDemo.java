package com.study.bc.signature;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.HexFormat;

import com.study.bc.BcSupport;

/**
 * 数字签名演示：RSA-SHA256 / ECDSA / DSA / Ed25519 / SM3withSM2。
 *
 * <p>签名 = 私钥签名、公钥验签，提供完整性 + 认证 + 不可否认。
 * SM3withSM2 与 Ed25519 需要 BC provider（JDK 原生不支持）。
 */
public final class SignatureDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private SignatureDemo() {
    }

    static {
        BcSupport.register(); // SM3withSM2 等需 BC provider
    }

    /** 生成指定算法的密钥对（DSA/Ed25519/RSA/EC）。 */
    public static KeyPair generateKeyPair(String algorithm, int keySize) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
            if ("EC".equals(algorithm)) {
                kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            } else if (keySize > 0) {
                kpg.initialize(keySize, RANDOM);
            }
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("密钥生成失败: " + algorithm, e);
        }
    }

    /** 用 JCE 签名算法签名，返回 hex 字符串。 */
    public static String sign(String jceAlgorithm, KeyPair keyPair, byte[] data) {
        try {
            Signature signature = Signature.getInstance(jceAlgorithm);
            signature.initSign(keyPair.getPrivate(), RANDOM);
            signature.update(data);
            return HEX.formatHex(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("签名失败: " + jceAlgorithm, e);
        }
    }

    /** 用 JCE 签名算法验签。 */
    public static boolean verify(String jceAlgorithm, KeyPair keyPair, byte[] data, String sigHex) {
        try {
            Signature signature = Signature.getInstance(jceAlgorithm);
            signature.initVerify(keyPair.getPublic());
            signature.update(data);
            return signature.verify(HEX.parseHex(sigHex));
        } catch (Exception e) {
            throw new IllegalStateException("验签失败: " + jceAlgorithm, e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        String msg = "数字签名演示：私钥签名，公钥验签";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);

        // (算法, 密钥生成算法, 密钥长度, JCE 签名算法)
        String[][] algos = {
                {"RSA-SHA256", "RSA", "2048", "SHA256withRSA"},
                {"ECDSA", "EC", "0", "SHA256withECDSA"},
                {"DSA", "DSA", "2048", "SHA256withDSA"},
                {"Ed25519", "Ed25519", "0", "Ed25519"},
                {"SM3withSM2", "EC", "0", "SM3withSM2"},
        };

        System.out.println("消息: " + msg);
        System.out.println();

        for (String[] algo : algos) {
            String name = algo[0];
            try {
                KeyPair keyPair = generateKeyPair(algo[1], Integer.parseInt(algo[2]));
                String sig = sign(algo[3], keyPair, data);
                boolean ok = verify(algo[3], keyPair, data, sig);
                System.out.printf("  %-12s 签名 %4d 字节, 验签: %s%n", name, sig.length() / 2, ok);
            } catch (Exception e) {
                System.out.printf("  %-12s 失败: %s%n", name, e.getMessage());
            }
        }
        System.out.println();
    }
}
