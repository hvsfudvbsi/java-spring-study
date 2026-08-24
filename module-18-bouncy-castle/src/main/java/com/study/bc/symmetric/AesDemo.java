package com.study.bc.symmetric;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * BC AES 分组密码演示：ECB / CBC / GCM / CTR 四种工作模式。
 *
 * <p>要点：
 * <ul>
 *   <li>ECB：相同明文块得到相同密文块，无扩散，应避免使用（真实场景弃用）。</li>
 *   <li>CBC：需要随机 IV，密文块与前一块异或，有扩散；填充用 PKCS7。</li>
 *   <li>CTR（SIC）：把 AES 变成流密码，按计数器加密，无需填充。</li>
 *   <li>GCM：认证加密（AEAD），密文附带认证标签，能检测篡改，现代首选。</li>
 * </ul>
 *
 * <p>适用场景：数据加密（TLS、数据库字段、文件存储）。GCM（认证加密）是现代首选；
 * CBC/CTR 仅遗留（须另配 MAC 或保证 IV 不重复）；ECB 不应使用。
 */
public final class AesDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private AesDemo() {
    }

    /** 生成随机 AES 密钥（128/192/256 位）。 */
    public static byte[] randomKey(int bits) {
        byte[] key = new byte[bits / 8];
        RANDOM.nextBytes(key);
        return key;
    }

    private static byte[] randomIv(int bytes) {
        byte[] iv = new byte[bytes];
        RANDOM.nextBytes(iv);
        return iv;
    }

    /** ECB 模式加密（PKCS7 填充）。 */
    public static byte[] ecbEncrypt(byte[] key, byte[] plain) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new AESEngine());
            cipher.init(true, new KeyParameter(key));
            return process(cipher, plain);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("ECB 加密失败", e);
        }
    }

    /** ECB 模式解密。 */
    public static byte[] ecbDecrypt(byte[] key, byte[] cipherText) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new AESEngine());
            cipher.init(false, new KeyParameter(key));
            return process(cipher, cipherText);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("ECB 解密失败", e);
        }
    }

    /** CBC 模式加密，返回 [IV || 密文]（IV 随机生成，随密文一起传输）。 */
    public static byte[] cbcEncrypt(byte[] key, byte[] plain) {
        try {
            byte[] iv = randomIv(16);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
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

    /** CBC 模式解密，入参为 [IV || 密文]。 */
    public static byte[] cbcDecrypt(byte[] key, byte[] in) {
        try {
            byte[] iv = new byte[16];
            System.arraycopy(in, 0, iv, 0, iv.length);
            byte[] body = new byte[in.length - iv.length];
            System.arraycopy(in, iv.length, body, 0, body.length);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
            cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));
            return process(cipher, body);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("CBC 解密失败", e);
        }
    }

    /**
     * CBC 密文块翻转攻击：翻转密文（[IV || C1...]）中第 blockIndex 块（0=IV）第 byteIndex 字节，
     * 返回篡改后的密文。
     *
     * <p>原理：CBC 解密 P[i] = D(C[i]) XOR C[i-1]，翻转 C[i-1] 的某字节会让 P[i] 的对应字节
     * 异或同样的 delta（可控修改），同时被翻转的 C[i-1] 自身解出的 P[i-1] 变乱码（副作用）。
     * 演示用：攻击者把 role=0 翻转成 role=1、金额 1000 改成 9000，且 PKCS7 填充仍可能校验通过。
     */
    public static byte[] cbcBitFlip(byte[] in, int blockIndex, int byteIndex, byte delta) {
        if (in.length < 16 || (in.length - 16) % 16 != 0) {
            throw new IllegalArgumentException("CBC 密文格式非法: 需 [IV(16) || 16 的倍数密文]");
        }
        if (blockIndex < 0 || byteIndex < 0 || byteIndex >= 16) {
            throw new IllegalArgumentException("翻转位置越界: block=" + blockIndex + ", byte=" + byteIndex);
        }
        int offset = blockIndex * 16 + byteIndex;
        if (offset >= in.length) {
            throw new IllegalArgumentException("翻转位置越界: block=" + blockIndex + ", byte=" + byteIndex);
        }
        byte[] tampered = in.clone();
        tampered[offset] ^= delta;
        return tampered;
    }

    /** CTR 模式加密（等价于解密，返回 [IV || 密文]）。 */
    public static byte[] ctrEncrypt(byte[] key, byte[] plain) {
        byte[] iv = randomIv(16);
        org.bouncycastle.crypto.StreamCipher cipher = new SICBlockCipher(new AESEngine());
        cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] body = processStream(cipher, plain);
        byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return out;
    }

    /** CTR 模式解密，入参为 [IV || 密文]。 */
    public static byte[] ctrDecrypt(byte[] key, byte[] in) {
        byte[] iv = new byte[16];
        System.arraycopy(in, 0, iv, 0, iv.length);
        byte[] body = new byte[in.length - iv.length];
        System.arraycopy(in, iv.length, body, 0, body.length);
        org.bouncycastle.crypto.StreamCipher cipher = new SICBlockCipher(new AESEngine());
        cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        return processStream(cipher, body);
    }

    /** GCM 认证加密，返回 [IV || 密文 || 标签]，标签 128 位。 */
    public static byte[] gcmEncrypt(byte[] key, byte[] plain) {
        try {
            byte[] iv = randomIv(12);
            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            cipher.init(true, new AEADParameters(new KeyParameter(key), 128, iv));
            byte[] body = process(cipher, plain);
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return out;
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("GCM 加密失败", e);
        }
    }

    /** GCM 认证解密，入参为 [IV || 密文 || 标签]；标签校验失败抛异常（篡改检测）。 */
    public static byte[] gcmDecrypt(byte[] key, byte[] in) {
        try {
            byte[] iv = new byte[12];
            System.arraycopy(in, 0, iv, 0, iv.length);
            byte[] body = new byte[in.length - iv.length];
            System.arraycopy(in, iv.length, body, 0, body.length);
            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            cipher.init(false, new AEADParameters(new KeyParameter(key), 128, iv));
            return process(cipher, body);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("GCM 解密失败（标签校验不过，密文可能被篡改）", e);
        }
    }

    /** 分组模式（ECB/CBC/GCM）通用处理。 */
    private static byte[] process(PaddedBufferedBlockCipher cipher, byte[] in) throws InvalidCipherTextException {
        byte[] out = new byte[cipher.getOutputSize(in.length)];
        int len = cipher.processBytes(in, 0, in.length, out, 0);
        len += cipher.doFinal(out, len);
        byte[] trimmed = new byte[len];
        System.arraycopy(out, 0, trimmed, 0, len);
        return trimmed;
    }

    private static byte[] process(GCMBlockCipher cipher, byte[] in) throws InvalidCipherTextException {
        byte[] out = new byte[cipher.getOutputSize(in.length)];
        int len = cipher.processBytes(in, 0, in.length, out, 0);
        len += cipher.doFinal(out, len);
        byte[] trimmed = new byte[len];
        System.arraycopy(out, 0, trimmed, 0, len);
        return trimmed;
    }

    /** 流模式（CTR）通用处理，无需填充，密文长度 = 明文长度。 */
    private static byte[] processStream(org.bouncycastle.crypto.StreamCipher cipher, byte[] in) {
        byte[] out = new byte[in.length];
        cipher.processBytes(in, 0, in.length, out, 0);
        return out;
    }

    /** 演示入口。 */
    public static void demo() {
        byte[] key = randomKey(128);
        String msg = "AES 分组密码演示：加密一段中文消息，观察各模式输出。";
        byte[] plain = msg.getBytes(StandardCharsets.UTF_8);

        System.out.println("明文: " + msg);
        System.out.println("明文长度: " + plain.length + " 字节, 密钥(hex): " + HEX.formatHex(key));
        System.out.println();

        byte[] ecb = ecbEncrypt(key, plain);
        System.out.println("AES-ECB 密文: " + HEX.formatHex(ecb) + " (" + ecb.length + " 字节)");
        System.out.println("  解密往返: " + new String(ecbDecrypt(key, ecb), StandardCharsets.UTF_8).equals(msg));

        byte[] cbc = cbcEncrypt(key, plain);
        System.out.println("AES-CBC 密文(IV+密文): " + HEX.formatHex(cbc) + " (" + cbc.length + " 字节)");
        System.out.println("  解密往返: " + new String(cbcDecrypt(key, cbc), StandardCharsets.UTF_8).equals(msg));

        byte[] ctr = ctrEncrypt(key, plain);
        System.out.println("AES-CTR 密文(IV+密文): " + HEX.formatHex(ctr) + " (" + ctr.length + " 字节, 无填充)");
        System.out.println("  解密往返: " + new String(ctrDecrypt(key, ctr), StandardCharsets.UTF_8).equals(msg));

        byte[] gcm = gcmEncrypt(key, plain);
        System.out.println("AES-GCM 密文(IV+密文+标签): " + HEX.formatHex(gcm) + " (" + gcm.length + " 字节)");
        System.out.println("  解密往返: " + new String(gcmDecrypt(key, gcm), StandardCharsets.UTF_8).equals(msg));
        try {
            byte[] tampered = gcm.clone();
            tampered[tampered.length - 1] ^= 0x01; // 翻转标签最后 1 位
            gcmDecrypt(key, tampered);
            System.out.println("  篡改检测: 未检测到（异常！）");
        } catch (IllegalStateException e) {
            System.out.println("  篡改检测: 已拒绝（" + e.getMessage() + "）");
        }
        System.out.println();

        cbcBitFlipDemo(key);
    }

    /** CBC 密文块翻转攻击演示：把 role=0 翻转成 role=1，并与 GCM 对照。 */
    private static void cbcBitFlipDemo(byte[] key) {
        String victim = "name=admin&role=0";
        byte[] plain = victim.getBytes(StandardCharsets.UTF_8);
        byte[] cbc = cbcEncrypt(key, plain);
        // 明文 17 字节 → 填充 32 字节 = [IV | C1(明文块1) | C2(明文块2)]。
        // 明文块1="name=admin&role="(16 字节)，'0' 在明文块 2 偏移 0：
        // 翻转 C1 偏移 0 ^= ('0'^'1') 即可把 role 改成 1，
        // 同时被翻转的 C1 解出的明文块 1 变成乱码（副作用）。
        byte delta = (byte) ('0' ^ '1'); // 0x01
        byte[] tampered = cbcBitFlip(cbc, 1, 0, delta);
        byte[] result = cbcDecrypt(key, tampered);
        // 结果 = 乱码(块1) + "1" + 填充(块2)；P2 偏移 0 应是 '1'，块 1 不再是原文
        boolean flipped = result.length > 16 && result[16] == '1';
        boolean corrupted = !new String(result, 0, 16, StandardCharsets.UTF_8).equals("name=admin&role=");

        System.out.println("CBC 密文块翻转攻击（无认证的后果）:");
        System.out.println("  原始明文: " + victim);
        System.out.println("  翻转 C1[0] ^= 0x01 后解密: [块1乱码 " + HEX.formatHex(java.util.Arrays.copyOf(result, 16))
                + "] " + new String(result, 16, result.length - 16, StandardCharsets.UTF_8));
        System.out.println("  -> 攻击者把 role=0 改成 role=1: " + flipped + "（目标块可控修改成功）");
        System.out.println("  -> 副作用: 被翻转的 C1 解出的前一块变乱码: " + corrupted);

        // GCM 对照：同样翻转密文体，标签校验直接拒绝
        byte[] gcm = gcmEncrypt(key, plain);
        byte[] gcmTampered = gcm.clone();
        gcmTampered[12 + 1] ^= delta; // 跳过 12 字节 IV，翻转第一个密文块偏移 1
        try {
            gcmDecrypt(key, gcmTampered);
            System.out.println("  GCM 对照: 未检测到（异常！）");
        } catch (IllegalStateException e) {
            System.out.println("  GCM 对照: 同样翻转被拒绝（认证标签保护，无法静默篡改）");
        }
        System.out.println();
    }
}
