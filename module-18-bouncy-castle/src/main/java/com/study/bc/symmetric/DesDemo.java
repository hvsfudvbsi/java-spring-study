package com.study.bc.symmetric;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * BC DES / 3DES（Triple DES）演示。
 *
 * <p>说明：DES 的 56 位密钥已可被暴力破解（1999 年 EFF 深钻机数小时攻破），
 * 3DES 用 3 个 DES 密钥（等效 112 位安全强度）作过渡，均被 AES 取代，
 * 此处仅作历史与兼容性学习。
 *
 * <p>适用场景：仅遗留兼容（旧金融系统、读卡器/EMV 过渡），新系统一律用 AES。
 */
public final class DesDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private DesDemo() {
    }

    /** DES 密钥必须 8 字节（56 位有效 + 8 位奇偶校验位）。 */
    public static byte[] randomDesKey() {
        byte[] key = new byte[8];
        RANDOM.nextBytes(key);
        return key;
    }

    /** 3DES 密钥必须 24 字节（K1||K2||K3，各 8 字节）。 */
    public static byte[] randomDesedeKey() {
        byte[] key = new byte[24];
        RANDOM.nextBytes(key);
        return key;
    }

    /** DES-CBC 加密，返回 [IV || 密文]。 */
    public static byte[] desCbcEncrypt(byte[] key, byte[] plain) {
        return cbcEncrypt(new DESEngine(), key, plain);
    }

    /** DES-CBC 解密，入参为 [IV || 密文]。 */
    public static byte[] desCbcDecrypt(byte[] key, byte[] in) {
        return cbcDecrypt(new DESEngine(), key, in);
    }

    /** 3DES-CBC 加密，返回 [IV || 密文]。 */
    public static byte[] desedeCbcEncrypt(byte[] key, byte[] plain) {
        return cbcEncrypt(new DESedeEngine(), key, plain);
    }

    /** 3DES-CBC 解密，入参为 [IV || 密文]。 */
    public static byte[] desedeCbcDecrypt(byte[] key, byte[] in) {
        return cbcDecrypt(new DESedeEngine(), key, in);
    }

    private static byte[] cbcEncrypt(org.bouncycastle.crypto.BlockCipher engine, byte[] key, byte[] plain) {
        try {
            byte[] iv = new byte[8];
            RANDOM.nextBytes(iv);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(engine));
            cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));
            byte[] body = process(cipher, plain);
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return out;
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("CBC 加密失败", e);
        }
    }

    private static byte[] cbcDecrypt(org.bouncycastle.crypto.BlockCipher engine, byte[] key, byte[] in) {
        try {
            byte[] iv = new byte[8];
            System.arraycopy(in, 0, iv, 0, iv.length);
            byte[] body = new byte[in.length - iv.length];
            System.arraycopy(in, iv.length, body, 0, body.length);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(engine));
            cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));
            return process(cipher, body);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("CBC 解密失败", e);
        }
    }

    private static byte[] process(PaddedBufferedBlockCipher cipher, byte[] in) throws InvalidCipherTextException {
        byte[] out = new byte[cipher.getOutputSize(in.length)];
        int len = cipher.processBytes(in, 0, in.length, out, 0);
        len += cipher.doFinal(out, len);
        byte[] trimmed = new byte[len];
        System.arraycopy(out, 0, trimmed, 0, len);
        return trimmed;
    }

    /** 演示入口。 */
    public static void demo() {
        String msg = "DES 与 3DES 演示，8 字节分组 + CBC 模式 + PKCS7 填充";
        byte[] plain = msg.getBytes(StandardCharsets.UTF_8);

        byte[] desKey = randomDesKey();
        byte[] des = desCbcEncrypt(desKey, plain);
        System.out.println("DES-CBC   密钥 " + desKey.length + " 字节, 密文 " + des.length + " 字节, 往返: "
                + new String(desCbcDecrypt(desKey, des), StandardCharsets.UTF_8).equals(msg));
        System.out.println("  密文(hex): " + HEX.formatHex(des));

        byte[] edeKey = randomDesedeKey();
        byte[] ede = desedeCbcEncrypt(edeKey, plain);
        System.out.println("3DES-CBC  密钥 " + edeKey.length + " 字节, 密文 " + ede.length + " 字节, 往返: "
                + new String(desedeCbcDecrypt(edeKey, ede), StandardCharsets.UTF_8).equals(msg));
        System.out.println("  密文(hex): " + HEX.formatHex(ede));
        System.out.println();
    }
}
