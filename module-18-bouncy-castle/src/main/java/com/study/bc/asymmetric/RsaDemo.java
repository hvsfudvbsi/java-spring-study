package com.study.bc.asymmetric;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.encodings.OAEPEncoding;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;

/**
 * BC RSA 非对称加密演示：PKCS#1 v1.5 与 OAEP 两种填充方案。
 *
 * <p>要点：
 * <ul>
 *   <li>RSA 本身是数学运算（模幂），必须配合填充方案使用，否则不安全。</li>
 *   <li>PKCS#1 v1.5：兼容性好，但存在 Bleichenbacher 预言机攻击，新系统应避免。</li>
 *   <li>OAEP（Optimal Asymmetric Encryption Padding）：随机化填充，提供 IND-CCA 安全，
 *       是现代推荐方案（RFC 8017）。</li>
 *   <li>RSA 加密长度受限：明文 ≤ 密钥字节数 - 填充开销（如 2048 位 + OAEP-SHA256 上限 190 字节）。</li>
 * </ul>
 *
 * <p>适用场景：证书体系（HTTPS）、密钥封装分发（加密对称密钥）、代码签名；
 * 明文长度受限，大块数据应改用混合加密（对称加密 + RSA 包密钥）。
 */
public final class RsaDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private RsaDemo() {
    }

    /** 生成 RSA 密钥对（2048 位，公钥指数 65537）。 */
    public static AsymmetricCipherKeyPair generateKeyPair(int bits) {
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(
                java.math.BigInteger.valueOf(65537), RANDOM, bits, 64));
        return generator.generateKeyPair();
    }

    /** PKCS#1 v1.5 加密。 */
    public static byte[] pkcs1Encrypt(RSAKeyParameters publicKey, byte[] plain) {
        return doCipher(true, new PKCS1Encoding(new RSAEngine()), publicKey, plain);
    }

    /** PKCS#1 v1.5 解密。 */
    public static byte[] pkcs1Decrypt(RSAKeyParameters privateKey, byte[] cipherText) {
        return doCipher(false, new PKCS1Encoding(new RSAEngine()), privateKey, cipherText);
    }

    /** OAEP 加密（SHA-256 作为哈希与掩码生成函数）。 */
    public static byte[] oaepEncrypt(RSAKeyParameters publicKey, byte[] plain) {
        return doCipher(true, new OAEPEncoding(new RSAEngine(), new org.bouncycastle.crypto.digests.SHA256Digest()),
                publicKey, plain);
    }

    /** OAEP 解密。 */
    public static byte[] oaepDecrypt(RSAKeyParameters privateKey, byte[] cipherText) {
        return doCipher(false, new OAEPEncoding(new RSAEngine(), new org.bouncycastle.crypto.digests.SHA256Digest()),
                privateKey, cipherText);
    }

    private static byte[] doCipher(boolean forEncryption, org.bouncycastle.crypto.AsymmetricBlockCipher cipher,
            CipherParameters key, byte[] in) {
        try {
            CipherParameters params = forEncryption ? new ParametersWithRandom(key, RANDOM) : key;
            cipher.init(forEncryption, params);
            return cipher.processBlock(in, 0, in.length);
        } catch (Exception e) {
            // 超长明文抛 DataLengthException，解密/填充错误抛 InvalidCipherTextException，统一包装
            throw new IllegalStateException(forEncryption ? "RSA 加密失败" : "RSA 解密失败", e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        AsymmetricCipherKeyPair pair = generateKeyPair(2048);
        RSAKeyParameters pub = (RSAKeyParameters) pair.getPublic();
        RSAKeyParameters priv = (RSAKeyParameters) pair.getPrivate();

        String msg = "RSA 非对称加密：公钥加密、私钥解密";
        byte[] plain = msg.getBytes(StandardCharsets.UTF_8);

        System.out.println("RSA-2048 密钥对: 公钥 " + pub.getModulus().bitLength() + " 位, 私钥已生成");
        System.out.println("明文: " + msg);
        System.out.println();

        byte[] pkcs1 = pkcs1Encrypt(pub, plain);
        System.out.println("PKCS#1 密文: " + HEX.formatHex(pkcs1) + " (" + pkcs1.length + " 字节 = 密钥长度)");
        System.out.println("  往返: " + new String(pkcs1Decrypt(priv, pkcs1), StandardCharsets.UTF_8).equals(msg));

        byte[] oaep = oaepEncrypt(pub, plain);
        System.out.println("OAEP    密文: " + HEX.formatHex(oaep) + " (" + oaep.length + " 字节 = 密钥长度)");
        System.out.println("  往返: " + new String(oaepDecrypt(priv, oaep), StandardCharsets.UTF_8).equals(msg));
        System.out.println();
    }
}
