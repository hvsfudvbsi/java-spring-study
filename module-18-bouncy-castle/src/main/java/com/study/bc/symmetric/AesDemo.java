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
    }
}
