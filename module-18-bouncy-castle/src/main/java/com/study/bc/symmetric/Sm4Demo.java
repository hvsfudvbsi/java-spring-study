package com.study.bc.symmetric;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * 国密 SM4 分组密码演示（GB/T 32907-2016）。
 *
 * <p>SM4 是国内商用密码标准分组算法，分组 128 位、密钥 128 位，
 * 与 AES-128 对标。本类演示 ECB / CBC / GCM 三种模式。
 *
 * <p>适用场景：国密合规系统的数据加密（金融/政务/无线局域网等），对标 AES-128；
 * 生产建议用 GCM（认证加密）而非裸 CBC/ECB。
 */
public final class Sm4Demo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Sm4Demo() {
    }

    /** SM4 密钥必须 16 字节（128 位）。 */
    public static byte[] randomKey() {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        return key;
    }

    /** SM4-ECB 加密（PKCS7 填充）。 */
    public static byte[] ecbEncrypt(byte[] key, byte[] plain) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine());
            cipher.init(true, new KeyParameter(key));
            return process(cipher, plain);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-ECB 加密失败", e);
        }
    }

    /** SM4-ECB 解密。 */
    public static byte[] ecbDecrypt(byte[] key, byte[] cipherText) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new SM4Engine());
            cipher.init(false, new KeyParameter(key));
            return process(cipher, cipherText);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-ECB 解密失败", e);
        }
    }

    /** SM4-CBC 加密，返回 [IV || 密文]。 */
    public static byte[] cbcEncrypt(byte[] key, byte[] plain) {
        try {
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new SM4Engine()));
            cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));
            byte[] body = process(cipher, plain);
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return out;
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-CBC 加密失败", e);
        }
    }

    /** SM4-CBC 解密，入参为 [IV || 密文]。 */
    public static byte[] cbcDecrypt(byte[] key, byte[] in) {
        try {
            byte[] iv = new byte[16];
            System.arraycopy(in, 0, iv, 0, iv.length);
            byte[] body = new byte[in.length - iv.length];
            System.arraycopy(in, iv.length, body, 0, body.length);
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new SM4Engine()));
            cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));
            return process(cipher, body);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-CBC 解密失败", e);
        }
    }

    /** SM4-GCM 认证加密，返回 [IV || 密文 || 标签]。 */
    public static byte[] gcmEncrypt(byte[] key, byte[] plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            cipher.init(true, new AEADParameters(new KeyParameter(key), 128, iv));
            byte[] body = process(cipher, plain);
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return out;
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-GCM 加密失败", e);
        }
    }

    /** SM4-GCM 认证解密，入参为 [IV || 密文 || 标签]。 */
    public static byte[] gcmDecrypt(byte[] key, byte[] in) {
        try {
            byte[] iv = new byte[12];
            System.arraycopy(in, 0, iv, 0, iv.length);
            byte[] body = new byte[in.length - iv.length];
            System.arraycopy(in, iv.length, body, 0, body.length);
            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            cipher.init(false, new AEADParameters(new KeyParameter(key), 128, iv));
            return process(cipher, body);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-GCM 解密失败（标签校验不过，密文可能被篡改）", e);
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

    private static byte[] process(GCMBlockCipher cipher, byte[] in) throws InvalidCipherTextException {
        byte[] out = new byte[cipher.getOutputSize(in.length)];
        int len = cipher.processBytes(in, 0, in.length, out, 0);
        len += cipher.doFinal(out, len);
        byte[] trimmed = new byte[len];
        System.arraycopy(out, 0, trimmed, 0, len);
        return trimmed;
    }

    /** 演示入口。 */
    public static void demo() {
        byte[] key = randomKey();
        String msg = "国密 SM4 分组密码演示（GB/T 32907-2016，对标 AES-128）";
        byte[] plain = msg.getBytes(StandardCharsets.UTF_8);

        System.out.println("明文: " + msg);
        System.out.println("SM4 密钥(16 字节): " + HEX.formatHex(key));
        System.out.println();

        byte[] ecb = ecbEncrypt(key, plain);
        System.out.println("SM4-ECB 密文: " + HEX.formatHex(ecb) + " (" + ecb.length + " 字节)");
        System.out.println("  往返: " + new String(ecbDecrypt(key, ecb), StandardCharsets.UTF_8).equals(msg));

        byte[] cbc = cbcEncrypt(key, plain);
        System.out.println("SM4-CBC 密文: " + HEX.formatHex(cbc) + " (" + cbc.length + " 字节)");
        System.out.println("  往返: " + new String(cbcDecrypt(key, cbc), StandardCharsets.UTF_8).equals(msg));

        byte[] gcm = gcmEncrypt(key, plain);
        System.out.println("SM4-GCM 密文: " + HEX.formatHex(gcm) + " (" + gcm.length + " 字节)");
        System.out.println("  往返: " + new String(gcmDecrypt(key, gcm), StandardCharsets.UTF_8).equals(msg));
        System.out.println();
    }
}
