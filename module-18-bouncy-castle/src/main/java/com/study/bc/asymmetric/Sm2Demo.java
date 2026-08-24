package com.study.bc.asymmetric;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;

/**
 * 国密 SM2 非对称算法演示（GB/T 32918，基于椭圆曲线，对标 ECDSA/ECIES）。
 *
 * <p>要点：
 * <ul>
 *   <li>SM2 密钥对基于推荐曲线 sm2p256v1（256 位）。</li>
 *   <li>SM2 加密输出 C1C3C2（C1=曲线点, C3=SM3 杂凑, C2=密文），自带完整性校验。</li>
 *   <li>SM2 签名：SM3withSM2，签名是 (r, s) 两个 32 字节大数，共 64~65 字节。</li>
 *   <li>国密要求：用户身份标识 ID 参与签名（默认 1234567812345678）。</li>
 * </ul>
 */
public final class Sm2Demo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    /** 默认用户身份标识（GB/T 32918.2 规定的缺省值）。 */
    public static final byte[] DEFAULT_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    private Sm2Demo() {
    }

    /** 生成 SM2 密钥对（sm2p256v1 曲线）。 */
    public static AsymmetricCipherKeyPair generateKeyPair() {
        X9ECParameters params = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domain = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domain, RANDOM));
        return generator.generateKeyPair();
    }

    /** SM2 加密（C1C3C2 模式）。 */
    public static byte[] encrypt(ECPublicKeyParameters publicKey, byte[] plain) {
        try {
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(publicKey, RANDOM));
            return engine.processBlock(plain, 0, plain.length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    /** SM2 解密。 */
    public static byte[] decrypt(ECPrivateKeyParameters privateKey, byte[] cipherText) {
        try {
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(false, privateKey);
            return engine.processBlock(cipherText, 0, cipherText.length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 解密失败", e);
        }
    }

    /** SM2 签名（SM3withSM2，携带默认用户 ID）。 */
    public static byte[] sign(ECPrivateKeyParameters privateKey, byte[] data) {
        try {
            SM2Signer signer = new SM2Signer();
            CipherParameters params = new ParametersWithID(new ParametersWithRandom(privateKey, RANDOM), DEFAULT_USER_ID);
            signer.init(true, params);
            signer.update(data, 0, data.length);
            return signer.generateSignature();
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /** SM2 验签。 */
    public static boolean verify(ECPublicKeyParameters publicKey, byte[] data, byte[] sig) {
        try {
            SM2Signer signer = new SM2Signer();
            CipherParameters params = new ParametersWithID(publicKey, DEFAULT_USER_ID);
            signer.init(false, params);
            signer.update(data, 0, data.length);
            return signer.verifySignature(sig);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 验签失败", e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        AsymmetricCipherKeyPair pair = generateKeyPair();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) pair.getPrivate();

        System.out.println("SM2 密钥对: 曲线 sm2p256v1, 公钥点 " + pub.getQ().getXCoord().toBigInteger().bitLength() + " 位");
        System.out.println();

        String msg = "国密 SM2 加密：C1C3C2 模式，密文自带完整性校验";
        byte[] plain = msg.getBytes(StandardCharsets.UTF_8);
        byte[] cipher = encrypt(pub, plain);
        byte[] decrypted = decrypt(priv, cipher);
        System.out.println("SM2 加密: 明文 " + plain.length + " 字节 -> 密文 " + cipher.length + " 字节");
        System.out.println("  密文(hex): " + HEX.formatHex(cipher));
        System.out.println("  解密往返: " + new String(decrypted, StandardCharsets.UTF_8).equals(msg));
        System.out.println();

        String signMsg = "国密 SM2 签名（SM3withSM2）";
        byte[] data = signMsg.getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(priv, data);
        System.out.println("SM2 签名: " + HEX.formatHex(sig) + " (" + sig.length + " 字节)");
        System.out.println("  验签(原数据)    : " + verify(pub, data, sig));
        System.out.println("  验签(篡改数据)  : " + verify(pub, (signMsg + "!").getBytes(StandardCharsets.UTF_8), sig));
        System.out.println();
    }
}
