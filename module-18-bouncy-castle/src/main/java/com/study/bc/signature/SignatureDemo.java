package com.study.bc.signature;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.util.HexFormat;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;

import com.study.bc.BcSupport;

/**
 * 数字签名演示：RSA-SHA256 / ECDSA / DSA / Ed25519 / SM3withSM2。
 *
 * <p>签名 = 私钥签名、公钥验签，提供完整性 + 认证 + 不可否认。
 * SM3withSM2 与 Ed25519 需要 BC provider（JDK 原生不支持）。
 *
 * <p>SM2 签名提供两种实现方式（对照）：
 * <ul>
 *   <li><b>JCE 方式</b>：{@code Signature.getInstance("SM3withSM2")}，经 BC provider；</li>
 *   <li><b>BC 底层 API</b>：{@link SM2Signer} + {@link SM3Digest}，直接驱动算法核心；</li>
 * </ul>
 * 两者签名格式兼容（都是 DER 编码的 (r, s)，默认用户 ID 1234567812345678），
 * 可互相验签（见 {@link #demo()} 互操作对照）。
 */
public final class SignatureDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    /** 国密 SM2 签名默认用户身份标识（GB/T 32918.2 缺省值，JCE 与底层 API 一致）。 */
    private static final byte[] DEFAULT_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

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

    // ============ BC 底层 API：SM2 签名（对照 JCE 方式） ============

    /** 底层方式生成 SM2 密钥对（推荐曲线 sm2p256v1）。 */
    public static AsymmetricCipherKeyPair sm2KeyPair() {
        X9ECParameters params = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domain = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domain, RANDOM));
        return generator.generateKeyPair();
    }

    /** 底层方式 SM2 签名：SM2Signer + SM3Digest，输出 DER 编码 (r, s)。 */
    public static byte[] sm2Sign(ECPrivateKeyParameters privateKey, byte[] data) {
        try {
            SM2Signer signer = new SM2Signer();
            CipherParameters params = new ParametersWithID(new ParametersWithRandom(privateKey, RANDOM), DEFAULT_USER_ID);
            signer.init(true, params);
            signer.update(data, 0, data.length);
            return signer.generateSignature();
        } catch (Exception e) {
            throw new IllegalStateException("SM2 底层签名失败", e);
        }
    }

    /** 底层方式 SM2 验签。 */
    public static boolean sm2Verify(ECPublicKeyParameters publicKey, byte[] data, byte[] sig) {
        try {
            SM2Signer signer = new SM2Signer();
            signer.init(false, new ParametersWithID(publicKey, DEFAULT_USER_ID));
            signer.update(data, 0, data.length);
            return signer.verifySignature(sig);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 底层验签失败", e);
        }
    }

    /** 底层密钥对 → JCE 密钥对（同一条 sm2p256v1 曲线，供 JCE 接口验签）。 */
    public static KeyPair toJceKeyPair(AsymmetricCipherKeyPair bcPair) {
        try {
            ECPrivateKeyParameters priv = (ECPrivateKeyParameters) bcPair.getPrivate();
            ECPublicKeyParameters pub = (ECPublicKeyParameters) bcPair.getPublic();
            // 公钥：底层曲线点 → JCE ECPublicKeySpec
            ECParameterSpec spec = EC5Util.convertToSpec(pub.getParameters());
            PublicKey jcePub = KeyFactory.getInstance("EC", "BC")
                    .generatePublic(new ECPublicKeySpec(EC5Util.convertPoint(pub.getQ()), spec));
            // 私钥：底层参数 → PKCS#8 编码 → JCE 私钥
            byte[] pkcs8 = PrivateKeyInfoFactory.createPrivateKeyInfo(priv).getEncoded();
            PrivateKey jcePriv = KeyFactory.getInstance("EC", "BC")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs8));
            return new KeyPair(jcePub, jcePriv);
        } catch (Exception e) {
            throw new IllegalStateException("底层密钥转 JCE 失败", e);
        }
    }

    /** JCE 密钥对 → 底层参数（供底层 API 验签）。 */
    public static AsymmetricCipherKeyPair toBcKeyPair(KeyPair jcePair) {
        try {
            ECPublicKeyParameters pub = (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(jcePair.getPublic());
            ECPrivateKeyParameters priv = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(jcePair.getPrivate());
            return new AsymmetricCipherKeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("JCE 密钥转底层失败", e);
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
                {"SM3withSM2(JCE)", "EC", "0", "SM3withSM2"},
        };

        System.out.println("消息: " + msg);
        System.out.println();

        for (String[] algo : algos) {
            String name = algo[0];
            try {
                KeyPair keyPair = generateKeyPair(algo[1], Integer.parseInt(algo[2]));
                String sig = sign(algo[3], keyPair, data);
                boolean ok = verify(algo[3], keyPair, data, sig);
                System.out.printf("  %-16s 签名 %4d 字节, 验签: %s%n", name, sig.length() / 2, ok);
            } catch (Exception e) {
                System.out.printf("  %-16s 失败: %s%n", name, e.getMessage());
            }
        }
        System.out.println();

        // SM2 底层 API 对照（sm2p256v1 曲线）：自身往返 + 与 JCE 方式互操作
        AsymmetricCipherKeyPair bcPair = sm2KeyPair();
        ECPrivateKeyParameters bcPriv = (ECPrivateKeyParameters) bcPair.getPrivate();
        ECPublicKeyParameters bcPub = (ECPublicKeyParameters) bcPair.getPublic();

        byte[] lowSig = sm2Sign(bcPriv, data);
        System.out.printf("  %-16s 签名 %4d 字节, 验签: %s (sm2p256v1, 底层 API)%n",
                "SM2Signer(底层)", lowSig.length, sm2Verify(bcPub, data, lowSig));

        // 互操作 1：底层签的，转成 JCE 密钥后用 JCE 接口验
        KeyPair jcePairFromBc = toJceKeyPair(bcPair);
        boolean cross1 = verify("SM3withSM2", jcePairFromBc, data, HEX.formatHex(lowSig));
        System.out.println("  [互操作] 底层签名 → JCE 验签: " + cross1);

        // 互操作 2：JCE 签的，转成底层参数后用底层 API 验
        String jceSig = sign("SM3withSM2", jcePairFromBc, data);
        AsymmetricCipherKeyPair bcFromJce = toBcKeyPair(jcePairFromBc);
        boolean cross2 = sm2Verify((ECPublicKeyParameters) bcFromJce.getPublic(), data, HEX.parseHex(jceSig));
        System.out.println("  [互操作] JCE 签名 → 底层验签: " + cross2);
        System.out.println();
    }
}
