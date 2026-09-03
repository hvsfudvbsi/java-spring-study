package com.study.bc.key;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DLBitString;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9ECPoint;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMEncryptor;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.util.encoders.Hex;

import com.study.bc.BcSupport;
import com.study.bc.asymmetric.Sm2Demo;

/**
 * 私钥文件解析与口令验证演示：解析 RSA / SM2（国密）私钥的各种标准格式，并验证口令是否正确。
 *
 * <p>覆盖的私钥文件格式（OpenSSL / 证书体系常见形态）：
 * <ul>
 *   <li>PKCS#8 未加密 PEM：{@code -----BEGIN PRIVATE KEY-----}（{@link #toPkcs8Pem} / {@link #parsePem}）；</li>
 *   <li>PKCS#8 加密 PEM：{@code -----BEGIN ENCRYPTED PRIVATE KEY-----}（{@link #toPkcs8EncryptedPem}，口令保护）；</li>
 *   <li>PKCS#1 传统 RSA PEM：{@code -----BEGIN RSA PRIVATE KEY-----}（{@link #toTraditionalPem}），
 *       可带 {@code Proc-Type: 4,ENCRYPTED} 头（{@link #toTraditionalEncryptedPem}）；</li>
 *   <li>SEC1 传统 EC PEM：{@code -----BEGIN EC PRIVATE KEY-----}（SM2 密钥即 EC 曲线 sm2p256v1，同样支持加密）；</li>
 *   <li>DER 二进制（PKCS#8 未加密）：{@link #toDer} / {@link #parseDer}。</li>
 * </ul>
 *
 * <p>要点：
 * <ul>
 *   <li>PEM 解析用 bcpkix 的 {@code PEMParser} + {@code JcaPEMKeyConverter}（BC provider）；</li>
 *   <li>加密 PEM 按类型分两条解密路径：PKCS#8 加密走
 *       {@code PKCS8EncryptedPrivateKeyInfo.decryptPrivateKeyInfo(JceOpenSSLPKCS8DecryptorProviderBuilder)}，
 *       传统格式加密走 {@code PEMEncryptedKeyPair.decryptKeyPair(JcePEMDecryptorProviderBuilder)}；</li>
 *   <li>口令验证 {@link #checkPassword}：加密文件用给定口令尝试解析（成功=true、失败=false），
 *       未加密文件无口令可验，恒返回 true；</li>
 *   <li>SM2 私钥与传统 EC 私钥同构（都是 EC 密钥），只是曲线换成国密 sm2p256v1——解析逻辑完全复用。</li>
 * </ul>
 *
 * <p>适用场景：私钥文件的导入/导出（密钥库迁移、TLS 服务器加载私钥、国密合规系统读取 SM2 私钥），
 * 加载加密私钥前先用 {@link #checkPassword} 校验口令，避免把「口令错误」当「文件损坏」处理。
 */
public final class PrivateKeyParserDemo {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PrivateKeyParserDemo() {
    }

    static {
        BcSupport.register(); // JcaPEMKeyConverter / 加解密 provider 均需 BC
    }

    /** 生成 RSA-2048 密钥对。 */
    public static KeyPair generateRsa() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, RANDOM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥生成失败", e);
        }
    }

    /** 生成 SM2 密钥对（sm2p256v1 曲线，返回 JCE EC 密钥对）。 */
    public static KeyPair generateSm2() {
        AsymmetricCipherKeyPair pair = Sm2Demo.generateKeyPair();
        return Sm2Demo.toJceKeyPair(pair);
    }

    /**
     * PKCS#8 未加密 PEM（BEGIN PRIVATE KEY）。
     *
     * <p>注意：BC 的 JcaPEMWriter 会把 RSA/EC 私钥（含 PrivateKeyInfo）自动转成传统格式
     * （RSA PRIVATE KEY / EC PRIVATE KEY），要强制输出 PKCS#8 必须手工拼 PEM 块。
     */
    public static String toPkcs8Pem(PrivateKey key) {
        return pemBlock("PRIVATE KEY", key.getEncoded(), null);
    }

    /** PKCS#8 加密 PEM（BEGIN ENCRYPTED PRIVATE KEY），用口令加密（pbeWithSHAAnd3-KeyTripleDES-CBC，OpenSSL 兼容）。 */
    public static String toPkcs8EncryptedPem(PrivateKey key, char[] password) {
        try {
            OutputEncryptor encryptor = new JceOpenSSLPKCS8EncryptorBuilder(
                    PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC)
                    .setProvider("BC")
                    .setRandom(RANDOM)
                    .setPassword(password)
                    .build();
            return pem(new JcaPKCS8Generator(key, encryptor).generate());
        } catch (Exception e) {
            throw new IllegalStateException("PKCS#8 加密私钥编码失败", e);
        }
    }

    /**
     * 传统格式 PEM（未加密）：RSA → BEGIN RSA PRIVATE KEY（PKCS#1），
     * EC/SM2 → BEGIN EC PRIVATE KEY（SEC1，含命名曲线 OID 与公钥点）。
     */
    public static String toTraditionalPem(PrivateKey key) {
        try {
            return pemBlock(traditionalType(key), toTraditional(key).toASN1Primitive().getEncoded(), null);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("传统格式私钥编码失败", e);
        }
    }

    /**
     * 传统格式加密 PEM：同 {@link #toTraditionalPem}，但带 {@code Proc-Type: 4,ENCRYPTED} 头
     * 与 DEK-Info（AES-256-CBC），对应 openssl 的 {@code openssl rsa -aes256} / {@code openssl ec -aes256}。
     */
    public static String toTraditionalEncryptedPem(PrivateKey key, char[] password) {
        try {
            PEMEncryptor encryptor = new JcePEMEncryptorBuilder("AES-256-CBC")
                    .setProvider("BC")
                    .setSecureRandom(RANDOM)
                    .build(password);
            return pemBlock(traditionalType(key), toTraditional(key).toASN1Primitive().getEncoded(), encryptor);
        } catch (Exception e) {
            throw new IllegalStateException("传统格式加密私钥编码失败", e);
        }
    }

    /** DER 二进制（PKCS#8 未加密）。 */
    public static byte[] toDer(PrivateKey key) {
        return key.getEncoded();
    }

    /** 解析 DER（PKCS#8 二进制）私钥。 */
    public static PrivateKey parseDer(byte[] der) {
        try {
            return new JcaPEMKeyConverter().setProvider("BC")
                    .getPrivateKey(PrivateKeyInfo.getInstance(der));
        } catch (Exception e) {
            throw new IllegalStateException("DER 私钥解析失败", e);
        }
    }

    /** 解析 PEM 私钥（未加密格式，等价于 {@code parsePem(pem, null)}）。 */
    public static PrivateKey parsePem(String pem) {
        return parsePem(pem, null);
    }

    /**
     * 解析 PEM 私钥，自动识别四种形态：PKCS#8 / PKCS#8 加密 / 传统 PKCS#1 / 传统 SEC1（含加密）。
     *
     * @param pem      PEM 文本
     * @param password 加密私钥的口令；未加密私钥传 null 即可
     * @return 解析出的 JCA 私钥
     * @throws IllegalStateException 格式非法或口令错误
     */
    public static PrivateKey parsePem(String pem, char[] password) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object obj = parser.readObject();
            if (obj instanceof PrivateKeyInfo pki) {
                // PKCS#8 未加密（BEGIN PRIVATE KEY）
                return converter.getPrivateKey(pki);
            }
            if (obj instanceof PEMKeyPair keyPair) {
                // 传统格式未加密（BEGIN RSA/EC/DSA PRIVATE KEY）
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            if (obj instanceof PEMEncryptedKeyPair encryptedPair) {
                // 传统格式加密（Proc-Type: 4,ENCRYPTED）
                requirePassword(password, pemType(pem));
                PEMKeyPair keyPair = encryptedPair.decryptKeyPair(
                        new JcePEMDecryptorProviderBuilder().setProvider("BC").build(password));
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            if (obj instanceof PKCS8EncryptedPrivateKeyInfo encryptedPki) {
                // PKCS#8 加密（BEGIN ENCRYPTED PRIVATE KEY）
                requirePassword(password, pemType(pem));
                PrivateKeyInfo pki = encryptedPki.decryptPrivateKeyInfo(
                        new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider("BC").build(password));
                return converter.getPrivateKey(pki);
            }
            throw new IllegalArgumentException("不支持的 PEM 内容: " + (obj == null ? "null" : obj.getClass().getSimpleName()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("私钥解析失败（格式非法或口令错误）", e);
        }
    }

    /**
     * 验证口令是否正确：对加密私钥文件用给定口令尝试解析，成功=true、失败=false；
     * 未加密私钥文件无口令可验，恒返回 true（先看 {@link #isEncrypted} 判断是否需要口令）。
     */
    public static boolean checkPassword(String pem, char[] password) {
        if (!isEncrypted(pem)) {
            return true; // 未加密：无口令可验
        }
        try {
            parsePem(pem, password);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** PEM 的 BEGIN 标记类型，如 {@code PRIVATE KEY} / {@code ENCRYPTED PRIVATE KEY} / {@code RSA PRIVATE KEY} / {@code EC PRIVATE KEY}。 */
    public static String pemType(String pem) {
        return pem.lines()
                .filter(line -> line.startsWith("-----BEGIN "))
                .findFirst()
                .map(line -> line.substring("-----BEGIN ".length(), line.length() - "-----".length()))
                .orElse("");
    }

    /** 是否为加密私钥：PKCS#8 加密（ENCRYPTED PRIVATE KEY）或传统格式带 Proc-Type: 4,ENCRYPTED 头。 */
    public static boolean isEncrypted(String pem) {
        return pem.contains("Proc-Type: 4,ENCRYPTED") || "ENCRYPTED PRIVATE KEY".equals(pemType(pem));
    }

    /** 通用 PEM 写出（JcaPEMWriter 自动按对象类型选择 BEGIN 标记）。 */
    private static String pem(Object obj) {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(obj);
            }
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("PEM 编码失败", e);
        }
    }

    /** 传统格式的 PEM 类型（BEGIN 标记）。 */
    private static String traditionalType(PrivateKey key) {
        ASN1ObjectIdentifier alg = PrivateKeyInfo.getInstance(key.getEncoded())
                .getPrivateKeyAlgorithm().getAlgorithm();
        if (PKCSObjectIdentifiers.rsaEncryption.equals(alg)) {
            return "RSA PRIVATE KEY";
        }
        if (X9ObjectIdentifiers.id_ecPublicKey.equals(alg)) {
            return "EC PRIVATE KEY";
        }
        throw new IllegalArgumentException("不支持的私钥算法: " + alg.getId());
    }

    /**
     * 私钥 → 传统格式 ASN.1 对象：RSA → RSAPrivateKey（PKCS#1）；EC/SM2 → SEC1 ECPrivateKey。
     *
     * <p>SEC1 的 parameters 优先放命名曲线 OID（OpenSSL/国密体系的互操作标准形态）：
     * PKCS#8 里是 OID 直接用；是显式曲线参数（如 Sm2Demo 生成的 PKCS#8 内嵌 sm2p256v1 全参数）
     * 就反向解析出对应的命名曲线 OID（sm2p256v1 → 1.2.156.10197.1.301）；
     * 只有查不到命名曲线的自定义曲线才原样带显式参数（此时用公开构造器重建
     * X9ECParameters 补全 fieldID，绕开 BC 1.80 解析后无法重编码的缺陷）。
     */
    private static ASN1Encodable toTraditional(PrivateKey key) {
        try {
            PrivateKeyInfo pki = PrivateKeyInfo.getInstance(key.getEncoded());
            ASN1ObjectIdentifier alg = pki.getPrivateKeyAlgorithm().getAlgorithm();
            if (PKCSObjectIdentifiers.rsaEncryption.equals(alg)) {
                return RSAPrivateKey.getInstance(pki.parsePrivateKey());
            }
            if (X9ObjectIdentifiers.id_ecPublicKey.equals(alg)) {
                ASN1Encodable algParams = pki.getPrivateKeyAlgorithm().getParameters();
                ASN1Encodable sec1Params;
                X9ECParameters x9;
                if (algParams instanceof ASN1ObjectIdentifier curveOid) {
                    // 命名曲线形式：SEC1 parameters 直接放曲线 OID
                    sec1Params = curveOid;
                    x9 = GMNamedCurves.getByOID(curveOid);
                    if (x9 == null) {
                        x9 = ECNamedCurveTable.getByOID(curveOid);
                    }
                } else {
                    // 显式曲线参数形式（如 Sm2Demo 生成的 PKCS#8 内嵌 sm2p256v1 全参数）
                    x9 = X9ECParameters.getInstance(algParams);
                    ASN1ObjectIdentifier namedOid = findNamedCurveOid(x9);
                    if (namedOid != null) {
                        sec1Params = namedOid;
                    } else {
                        // 自定义曲线：重建 X9ECParameters（公开构造器会补全 fieldID，可正常重编码）
                        sec1Params = new X9ECParameters(x9.getCurve(), new X9ECPoint(x9.getG(), false),
                                x9.getN(), x9.getH(), x9.getSeed());
                    }
                }
                if (x9 == null) {
                    throw new IllegalArgumentException("未知椭圆曲线参数");
                }
                BigInteger d = ECPrivateKey.getInstance(pki.parsePrivateKey()).getKey();
                // 由私钥 d 重算公钥点 G*d（SEC1 的 [1] publicKey 字段，OpenSSL 惯例必带）
                ECPoint q = x9.getG().multiply(d).normalize();
                // 第一个 int 参数是曲线阶的位长（不是 SEC1 version），OCTET STRING 按它定长
                return new ECPrivateKey(x9.getN().bitLength(), d, new DLBitString(q.getEncoded(false)), sec1Params);
            }
            throw new IllegalArgumentException("不支持的私钥算法: " + alg.getId());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("传统格式私钥编码失败", e);
        }
    }

    /**
     * 手工拼传统格式 PEM 块：BEGIN/END 标记 + 64 字符换行 Base64；
     * 带 encryptor 时加 Proc-Type/DEK-Info 头并加密内容（与 OpenSSL 的 PEM 加密格式一致）。
     */
    private static String pemBlock(String type, byte[] der, PEMEncryptor encryptor) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN ").append(type).append("-----\n");
            if (encryptor != null) {
                sb.append("Proc-Type: 4,ENCRYPTED\n");
                sb.append("DEK-Info: ").append(encryptor.getAlgorithm()).append(',')
                        .append(Hex.toHexString(encryptor.getIV())).append("\n\n");
                der = encryptor.encrypt(der);
            }
            String b64 = Base64.getEncoder().encodeToString(der);
            for (int i = 0; i < b64.length(); i += 64) {
                sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
            }
            sb.append("-----END ").append(type).append("-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("PEM 编码失败", e);
        }
    }

    /**
     * 两把私钥的密钥材料是否一致：先比 PKCS#8 字节（RSA 无参数、格式固定，恒可字节级一致）；
     * EC/SM2 参数形态可能不同（显式参数 vs 命名曲线 OID），再比私钥标量 d 与曲线。
     */
    static boolean sameKeyMaterial(PrivateKey expected, PrivateKey parsed) {
        if (Arrays.equals(expected.getEncoded(), parsed.getEncoded())) {
            return true;
        }
        try {
            ECPrivateKeyParameters exp = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(expected);
            ECPrivateKeyParameters act = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(parsed);
            return exp.getD().equals(act.getD())
                    && exp.getParameters().getCurve().equals(act.getParameters().getCurve());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 反向解析显式曲线参数对应的命名曲线 OID：先查标准命名曲线表（secp256r1 等），
     * 再查国密曲线表（sm2p256v1 等，独立于 ECNamedCurveTable）。查不到返回 null。
     */
    private static ASN1ObjectIdentifier findNamedCurveOid(X9ECParameters x9) {
        for (Enumeration<String> names = ECNamedCurveTable.getNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            if (sameCurve(ECNamedCurveTable.getByName(name), x9)) {
                return ECNamedCurveTable.getOID(name);
            }
        }
        for (Enumeration<String> names = GMNamedCurves.getNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            if (sameCurve(GMNamedCurves.getByName(name), x9)) {
                return GMNamedCurves.getOID(name);
            }
        }
        return null;
    }

    /** 两条命名曲线的数学参数是否一致（域 + 系数 + 阶，DER 编码确定性比较）。 */
    private static boolean sameCurve(X9ECParameters candidate, X9ECParameters x9) {
        return candidate != null
                && candidate.getN().equals(x9.getN())
                && candidate.getCurve().equals(x9.getCurve());
    }

    private static void requirePassword(char[] password, String type) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("加密私钥（" + type + "）必须提供口令");
        }
    }

    /** 演示入口。 */
    public static void demo() {
        char[] password = "changeit".toCharArray();
        char[] wrong = "wrong-password".toCharArray();

        System.out.println("== 私钥解析与口令验证（RSA + SM2）==");
        System.out.println();

        // 1. RSA：四种 PEM 形态 + DER，全部解析还原
        KeyPair rsa = generateRsa();
        System.out.println("1) RSA-2048 私钥，各格式编码与解析还原：");
        String[] rsaPems = {
                toPkcs8Pem(rsa.getPrivate()),
                toPkcs8EncryptedPem(rsa.getPrivate(), password),
                toTraditionalPem(rsa.getPrivate()),
                toTraditionalEncryptedPem(rsa.getPrivate(), password)};
        for (String pem : rsaPems) {
            String tag = pemType(pem) + (isEncrypted(pem) ? "（加密）" : "（未加密）");
            boolean restored = sameKeyMaterial(rsa.getPrivate(), parsePem(pem, password));
            System.out.println("   " + tag + " -> 解析还原=" + restored);
        }
        System.out.println("   DER(PKCS#8 二进制) -> 解析还原="
                + sameKeyMaterial(rsa.getPrivate(), parseDer(toDer(rsa.getPrivate()))));
        System.out.println("   口令验证：加密 PKCS#8 正确口令=" + checkPassword(rsaPems[1], password)
                + "、错误口令=" + checkPassword(rsaPems[1], wrong)
                + "；未加密私钥任意口令=" + checkPassword(rsaPems[0], wrong));
        System.out.println();

        // 2. SM2：同样四种格式 + 解析出的私钥直接做 SM3withSM2 签名
        KeyPair sm2 = generateSm2();
        System.out.println("2) SM2 私钥（sm2p256v1 曲线），各格式编码与解析还原：");
        String[] sm2Pems = {
                toPkcs8Pem(sm2.getPrivate()),
                toPkcs8EncryptedPem(sm2.getPrivate(), password),
                toTraditionalPem(sm2.getPrivate()),
                toTraditionalEncryptedPem(sm2.getPrivate(), password)};
        for (String pem : sm2Pems) {
            String tag = pemType(pem) + (isEncrypted(pem) ? "（加密）" : "（未加密）");
            boolean restored = sameKeyMaterial(sm2.getPrivate(), parsePem(pem, password));
            System.out.println("   " + tag + " -> 解析还原=" + restored);
        }
        System.out.println("   DER(PKCS#8 二进制) -> 解析还原="
                + sameKeyMaterial(sm2.getPrivate(), parseDer(toDer(sm2.getPrivate()))));
        System.out.println("   口令验证：加密传统 EC 正确口令=" + checkPassword(sm2Pems[3], password)
                + "、错误口令=" + checkPassword(sm2Pems[3], wrong));

        // 3. 解析出的私钥可直接用于密码学操作（签名）
        byte[] data = "私钥解析可用性验证".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            ECPrivateKeyParameters priv = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(
                    parsePem(sm2Pems[3], password));
            ECPublicKeyParameters pub = (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(sm2.getPublic());
            byte[] sig = Sm2Demo.sign(priv, data);
            System.out.println("3) 用「加密传统 EC PEM」解析出的 SM2 私钥签名，原公钥验签="
                    + Sm2Demo.verify(pub, data, sig));
        } catch (Exception e) {
            System.out.println("3) SM2 签名验证异常: " + e.getMessage());
        }
        System.out.println();
    }
}