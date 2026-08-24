package com.study.bc.mac;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * HMAC（基于哈希的消息认证码）演示。
 *
 * <p>HMAC = H(key ⊕ opad || H(key ⊕ ipad || message))，带密钥的哈希，
 * 用于消息完整性 + 来源认证（共享密钥双方）。相比裸哈希，密钥参与计算，
 * 无法离线暴力破解，也不会被长度扩展攻击。
 */
public final class HmacDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private HmacDemo() {
    }

    /** 按算法名创建 HMAC 实例，未知算法返回 null。 */
    public static Mac mac(String algorithm) {
        return switch (algorithm) {
            case "HmacMD5" -> new HMac(new MD5Digest());
            case "HmacSHA256" -> new HMac(new SHA256Digest());
            case "HmacSHA512" -> new HMac(new SHA512Digest());
            default -> null;
        };
    }

    /** 计算 HMAC。 */
    public static byte[] hmac(String algorithm, byte[] key, byte[] data) {
        Mac mac = mac(algorithm);
        if (mac == null) {
            throw new IllegalArgumentException("未知 HMAC 算法: " + algorithm);
        }
        mac.init(new KeyParameter(key));
        mac.update(data, 0, data.length);
        byte[] out = new byte[mac.getMacSize()];
        mac.doFinal(out, 0);
        return out;
    }

    /** 生成随机密钥（建议 ≥ 摘要长度）。 */
    public static byte[] randomKey(int bytes) {
        byte[] key = new byte[bytes];
        RANDOM.nextBytes(key);
        return key;
    }

    /** 演示入口。 */
    public static void demo() {
        byte[] key = randomKey(32);
        String msg = "HMAC 消息认证码演示";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);

        System.out.println("密钥(32 字节): " + HEX.formatHex(key));
        System.out.println("消息: " + msg);
        System.out.println();

        for (String algo : new String[] {"HmacMD5", "HmacSHA256", "HmacSHA512"}) {
            byte[] tag = hmac(algo, key, data);
            byte[] wrong = hmac(algo, randomKey(32), data);
            System.out.printf("  %-11s (%2d 字节): %s%n", algo, tag.length, HEX.formatHex(tag));
            System.out.printf("    错钥 MAC   : %s (一致=%b)%n", HEX.formatHex(wrong), java.util.Arrays.equals(tag, wrong));
        }
        System.out.println();
    }
}
