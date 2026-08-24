package com.study.bc.hash;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.digests.SM3Digest;

/**
 * BC 哈希算法演示：MD5 / SHA-1 / SHA-256 / SHA-3 / 国密 SM3，以及加盐哈希。
 *
 * <p>说明：MD5 与 SHA-1 已不推荐用于安全场景（碰撞/长度扩展攻击），此处仅作算法对比。
 * 密码存储应使用加盐 + 慢哈希（如 BCrypt/Argon2），本类用 SHA-256 + 随机盐演示思路。
 */
public final class HashDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private HashDemo() {
    }

    /** 按算法名创建 BC 摘要实例，返回 null 表示未知算法。 */
    public static Digest digest(String algorithm) {
        return switch (algorithm) {
            case "MD5" -> new MD5Digest();
            case "SHA-1" -> new SHA1Digest();
            case "SHA-256" -> new SHA256Digest();
            case "SHA3-256" -> new SHA3Digest(256);
            case "SM3" -> new SM3Digest();
            default -> null;
        };
    }

    /** 用指定 BC 摘要算法计算哈希。 */
    public static byte[] hash(String algorithm, byte[] data) {
        Digest digest = digest(algorithm);
        if (digest == null) {
            throw new IllegalArgumentException("未知摘要算法: " + algorithm);
        }
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    /** 加盐哈希：随机 16 字节盐拼在原文前，返回 [盐 || 哈希]（盐无需保密，需随哈希一起存储）。 */
    public static byte[] saltedHash(byte[] data) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] salted = new byte[salt.length + data.length];
        System.arraycopy(salt, 0, salted, 0, salt.length);
        System.arraycopy(data, 0, salted, salt.length, data.length);
        byte[] digest = hash("SHA-256", salted);
        byte[] out = new byte[salt.length + digest.length];
        System.arraycopy(salt, 0, out, 0, salt.length);
        System.arraycopy(digest, 0, out, salt.length, digest.length);
        return out;
    }

    /** 校验加盐哈希：用存储的盐重算并比较，返回是否匹配（常数时间比较）。 */
    public static boolean verifySaltedHash(byte[] data, byte[] stored) {
        int saltLen = 16;
        byte[] salt = new byte[saltLen];
        System.arraycopy(stored, 0, salt, 0, saltLen);
        byte[] salted = new byte[saltLen + data.length];
        System.arraycopy(salt, 0, salted, 0, saltLen);
        System.arraycopy(data, 0, salted, saltLen, data.length);
        byte[] digest = hash("SHA-256", salted);
        byte[] storedDigest = new byte[stored.length - saltLen];
        System.arraycopy(stored, saltLen, storedDigest, 0, storedDigest.length);
        return java.security.MessageDigest.isEqual(digest, storedDigest);
    }

    /** 演示入口：计算各算法哈希并输出。 */
    public static void demo() {
        String msg = "Bouncy Castle 哈希演示";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        System.out.println("原始消息: " + msg);
        System.out.println("长度(字节): " + data.length);
        System.out.println();
        for (String algo : new String[] {"MD5", "SHA-1", "SHA-256", "SHA3-256", "SM3"}) {
            byte[] h = hash(algo, data);
            System.out.printf("  %-9s (%2d 字节): %s%n", algo, h.length, HEX.formatHex(h));
        }
        System.out.println();
        byte[] salted = saltedHash(data);
        System.out.println("加盐 SHA-256 (盐16+哈希32 = " + salted.length + " 字节): " + HEX.formatHex(salted));
        System.out.println("  校验正确密码: " + verifySaltedHash(data, salted));
        System.out.println("  校验错误密码: " + verifySaltedHash("wrong".getBytes(StandardCharsets.UTF_8), salted));
        System.out.println();
    }
}
