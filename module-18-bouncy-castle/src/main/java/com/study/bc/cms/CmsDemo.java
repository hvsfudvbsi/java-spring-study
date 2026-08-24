package com.study.bc.cms;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.KeyTransRecipient;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.RecipientOperator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.operator.AsymmetricKeyWrapper;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.GenericKey;
import org.bouncycastle.operator.InputDecryptor;
import org.bouncycastle.operator.OperatorException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import com.study.bc.BcSupport;
import com.study.bc.asymmetric.Sm2Demo;
import com.study.bc.cert.CertificateDemo;

/**
 * CMS（Cryptographic Message Syntax，RFC 5652，即 PKCS#7 的演进标准）演示。
 *
 * <p>覆盖两类最常用内容类型：
 * <ul>
 *   <li><b>SignedData（PKCS#7 签名）</b>：附件（attach）与分离（detach）两种——
 *   attach 把原文内嵌进签名（.p7m 场景），detach 只给签名、原文另行传输（.p7s/.sig 场景）；</li>
 *   <li><b>EnvelopedData（数字信封）</b>：随机生成对称密钥加密数据，再用收件人公钥\n *   加密该对称密钥（密钥传输 key transport），收件人用私钥解出对称密钥再解密数据。</li>
 * </ul>
 *
 * <p>适用场景：S/MIME 邮件签名/加密、PDF/代码签名（attach）、软件发布签名（detach .sig）、
 * 数据安全交换的数字信封（与国密 GmDemo 的 SM2+SM3+SM4 信封是同一思想，这里是标准 CMS 格式）。
 */
public final class CmsDemo {

    private static final HexFormat HEX = HexFormat.of();

    /** SM4 内容加密算法 OID（1.2.156.10197.1.104）。 */
    private static final ASN1ObjectIdentifier SM4_CBC_OID = new ASN1ObjectIdentifier("1.2.156.10197.1.104");

    /** SM2 密钥封装算法 OID（1.2.156.10197.1.301.3.2.1，SM2 加密模式）。 */
    private static final ASN1ObjectIdentifier SM2_KEM_OID = new ASN1ObjectIdentifier("1.2.156.10197.1.301.3.2.1");

    /** 国密签名算法：SM3 摘要 + SM2 签名（OID 1.2.156.10197.1.501）。 */
    private static final String GM_SIGNATURE_ALGORITHM = "SM3withSM2";

    private static volatile boolean sm4AliasesRegistered;

    private CmsDemo() {
    }

    static {
        BcSupport.register(); // CMS JCA 桥接需 BC provider
        registerSm4Aliases();
    }

    /**
     * 补注册 BC 1.80 provider 缺失的 SM4 OID 别名。
     *
     * <p>BC 1.80 的 SM4 只注册了裸算法名 "SM4"（映射到 ECB 模式），
     * 国密 OID 1.2.156.10197.1.104 没有任何别名。若直接用该 OID 构造
     * {@code JceCMSContentEncryptorBuilder}，内容加密实际会走 ECB（参数里的 IV 被忽略，
     * 标签写着 CBC 实为 ECB）。补注册两个关键别名后 OID 查找得到完整变换串，CMS 才走真 CBC：
     * <ul>
     *   <li>{@code Cipher.SM4/CBC/PKCS7Padding} → SM4 引擎（JDK 按变换串切模式/填充）；</li>
     *   <li>{@code Alg.Alias.Cipher.<SM4 OID>} → "SM4/CBC/PKCS7Padding"（注意不能只注册此别名
     *   指向完整变换串——provider 不做斜杠剥离会 NoSuchAlgorithmException，必须同时注册服务）。</li>
     * </ul>
     * 另补 KeyGenerator/AlgorithmParameters/AlgorithmParameterGenerator 别名供 CMS 生成 CEK 与 IV。
     * 重复调用幂等（覆盖相同键）。
     */
    private static void registerSm4Aliases() {
        if (sm4AliasesRegistered) {
            return;
        }
        java.security.Provider bc = java.security.Security.getProvider("BC");
        bc.put("Cipher.SM4/CBC/PKCS7Padding", "org.bouncycastle.jcajce.provider.symmetric.SM4$ECB");
        bc.put("Alg.Alias.Cipher." + SM4_CBC_OID, "SM4/CBC/PKCS7Padding");
        bc.put("Alg.Alias.KeyGenerator." + SM4_CBC_OID, "SM4");
        bc.put("Alg.Alias.AlgorithmParameters." + SM4_CBC_OID, "SM4");
        bc.put("Alg.Alias.AlgorithmParameterGenerator." + SM4_CBC_OID, "SM4");
        sm4AliasesRegistered = true;
    }

    /** 附件签名（attach）：原文内嵌进 SignedData，验证时不需另带原文。 */
    public static byte[] signAttached(KeyPair signer, X509Certificate signerCert, byte[] data) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(signerInfo(signer, signerCert));
            CMSSignedData signed = generator.generate(new CMSProcessableByteArray(data), true);
            return signed.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("CMS 附件签名失败", e);
        }
    }

    /** 分离签名（detach）：签名与原文分离，验证时必须提供原文。 */
    public static byte[] signDetached(KeyPair signer, X509Certificate signerCert, byte[] data) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(signerInfo(signer, signerCert));
            CMSSignedData signed = generator.generate(new CMSProcessableByteArray(data), false);
            return signed.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("CMS 分离签名失败", e);
        }
    }

    /**
     * 验证附件签名：解析 SignedData → 校验签名者签名 → 取出内嵌原文比较。
     *
     * @return 验证结果（签名有效 + 内嵌内容与原文一致）
     */
    public static boolean verifyAttached(X509Certificate signerCert, byte[] signed, byte[] expectedContent) {
        try {
            CMSSignedData cms = new CMSSignedData(signed);
            if (!verifySignerInfos(signerCert, cms.getSignerInfos())) {
                return false;
            }
            // attach：内容内嵌，取出来应与原文一致
            byte[] embedded = (byte[]) cms.getSignedContent().getContent();
            return java.util.Arrays.equals(embedded, expectedContent);
        } catch (Exception e) {
            return false;
        }
    }

    /** 验证分离签名：需要原文参与构造 CMSSignedData。 */
    public static boolean verifyDetached(PublicKey signerPub, byte[] data, byte[] signed) {
        try {
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(data), signed);
            return verifySignerInfos(signerPub, cms.getSignerInfos());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 数字信封（EnvelopedData）：随机对称密钥（AES-128-CBC）加密数据，RSA 公钥封装对称密钥。
     *
     * <p>注：内容加密用 CBC 而非 GCM——GCM 的 AEAD 参数在 openssl smime -decrypt 命令下
     * 报 cipher parameter error（OpenSSL 3 的 PKCS7_decrypt 限制），CBC 是 S/MIME 常规做法，
     * 与 openssl 命令行互操作最稳。
     */
    public static byte[] envelop(X509Certificate recipientCert, byte[] data) {
        try {
            CMSEnvelopedDataGenerator generator = new CMSEnvelopedDataGenerator();
            generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(recipientCert).setProvider("BC"));
            CMSEnvelopedData enveloped = generator.generate(new CMSProcessableByteArray(data),
                    new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES128_CBC).setProvider("BC").build());
            return enveloped.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("CMS 数字信封封装失败", e);
        }
    }

    /** DER 字节 → PEM 文本（base64 换行 64 字符 + BEGIN/END 标记）。 */
    public static String toPem(byte[] der, String label) {
        String body = java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    /** 把 DER 写成 PEM 文件（自动建目录）。 */
    public static void writePemFile(java.nio.file.Path dir, String fileName, String label, byte[] der)
            throws java.io.IOException {
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve(fileName), toPem(der, label), StandardCharsets.US_ASCII);
    }

    /** 开启数字信封：收件人私钥解出对称密钥并解密数据。 */
    public static byte[] openEnvelope(PrivateKey recipientKey, byte[] enveloped) {
        try {
            CMSEnvelopedData cms = new CMSEnvelopedData(enveloped);
            RecipientInformationStore recipients = cms.getRecipientInfos();
            RecipientInformation recipient = recipients.getRecipients().iterator().next();
            return recipient.getContent(new JceKeyTransEnvelopedRecipient(recipientKey).setProvider("BC"));
        } catch (Exception e) {
            throw new IllegalStateException("CMS 数字信封开启失败", e);
        }
    }

    // ==================== 国密版：SM2 + SM3 + SM4 ====================

    /** 生成 SM2 密钥对（推荐曲线 sm2p256v1），复用 Sm2Demo 底层生成再转 JCE。 */
    public static KeyPair gmKeyPair() {
        return Sm2Demo.toJceKeyPair(Sm2Demo.generateKeyPair());
    }

    /** 用 SM3withSM2 签发自签名证书（国密证书的签名算法是 SM3withSM2 而非 SHA256withRSA）。 */
    public static X509Certificate gmSelfSignedCert(KeyPair keyPair, X500Name name) {
        try {
            long now = System.currentTimeMillis();
            ContentSigner signer = new JcaContentSignerBuilder(GM_SIGNATURE_ALGORITHM).setProvider("BC")
                    .build(keyPair.getPrivate());
            X509CertificateHolder holder = new JcaX509v3CertificateBuilder(name, new BigInteger(64, new SecureRandom()),
                    new Date(now - 86_400_000L), new Date(now + 3650L * 86_400_000L), name, keyPair.getPublic())
                    .build(signer);
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 自签名证书生成失败", e);
        }
    }

    /**
     * 国密附件签名（SignedData，SM3withSM2）：SM3 摘要 + SM2 签名，原文内嵌进签名。
     * 与 RSA 版唯一区别是签名算法，验证逻辑通用。
     */
    public static byte[] gmSignAttached(KeyPair signer, X509Certificate signerCert, byte[] data) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(signerInfo(signer, signerCert, GM_SIGNATURE_ALGORITHM));
            return generator.generate(new CMSProcessableByteArray(data), true).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("国密附件签名失败（SM3withSM2）", e);
        }
    }

    /** 国密分离签名（SignedData，SM3withSM2）：只给签名，原文另行传输。 */
    public static byte[] gmSignDetached(KeyPair signer, X509Certificate signerCert, byte[] data) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(signerInfo(signer, signerCert, GM_SIGNATURE_ALGORITHM));
            return generator.generate(new CMSProcessableByteArray(data), false).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("国密分离签名失败（SM3withSM2）", e);
        }
    }

    /** 验证国密附件签名（签名有效 + 内嵌内容与原文一致）。 */
    public static boolean gmVerifyAttached(X509Certificate signerCert, byte[] signed, byte[] expectedContent) {
        try {
            CMSSignedData cms = new CMSSignedData(signed);
            if (!verifySignerInfos(signerCert, cms.getSignerInfos())) {
                return false;
            }
            byte[] embedded = (byte[]) cms.getSignedContent().getContent();
            return java.util.Arrays.equals(embedded, expectedContent);
        } catch (Exception e) {
            return false;
        }
    }

    /** 验证国密分离签名（带原文重建后验签）。 */
    public static boolean gmVerifyDetached(PublicKey signerPub, byte[] data, byte[] signed) {
        try {
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(data), signed);
            return verifySignerInfos(signerPub, cms.getSignerInfos());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 国密数字信封（EnvelopedData，GM/T 0010 形态）：
     * SM4-CBC 加密数据（CEK 随机生成）+ SM2 加密 CEK（key transport）。
     *
     * <p>BC 1.80 的 CMS 没有 SM2 密钥封装的 JCE 封装（GMCipherSpi 不支持 wrap/unwrap、
     * 无 KeyAgreement.SM2），因此用底层 {@link SM2Engine} 实现自定义 {@link AsymmetricKeyWrapper}：
     * 用收件人 SM2 公钥加密 CEK（输出 C1+C2+C3），OID 记 1.2.156.10197.1.301.3.2.1。
     * 内容加密经 {@link #registerSm4Aliases()} 补注册后走真 SM4-CBC。
     */
    public static byte[] gmEnvelop(PublicKey recipientPub, byte[] data) {
        try {
            ECPublicKeyParameters pub = (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(recipientPub);
            CMSEnvelopedDataGenerator generator = new CMSEnvelopedDataGenerator();
            // SKI 用固定值演示；真实场景取收件人证书的 SubjectKeyIdentifier
            generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(
                    new byte[] {1, 2, 3, 4, 5, 6, 7, 8}, sm2Wrapper(pub)));
            CMSEnvelopedData enveloped = generator.generate(new CMSProcessableByteArray(data),
                    new JceCMSContentEncryptorBuilder(SM4_CBC_OID).setProvider("BC").build());
            return enveloped.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("国密信封封装失败（SM2+SM4）", e);
        }
    }

    /**
     * 开拆国密数字信封：SM2 私钥解出 CEK（SM3 完整性校验失败即拒绝），再 SM4-CBC 解密内容。
     *
     * <p>收件人实现自定义 {@link KeyTransRecipient}：SM2Engine 解密 CEK，内容解密用
     * 显式 "SM4/CBC/PKCS7Padding" 变换 + 从信封内容加密算法参数解析的 IV。
     */
    public static byte[] gmOpenEnvelope(PrivateKey recipientKey, byte[] enveloped) {
        try {
            ECPrivateKeyParameters priv = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(recipientKey);
            CMSEnvelopedData cms = new CMSEnvelopedData(enveloped);
            RecipientInformationStore recipients = cms.getRecipientInfos();
            RecipientInformation recipient = recipients.getRecipients().iterator().next();
            return recipient.getContent(new KeyTransRecipient() {
                @Override
                public RecipientOperator getRecipientOperator(AlgorithmIdentifier keyEncAlg,
                        AlgorithmIdentifier contentEncAlg, byte[] encCek) throws CMSException {
                    try {
                        SM2Engine engine = new SM2Engine();
                        engine.init(false, priv);
                        byte[] cek = engine.processBlock(encCek, 0, encCek.length);
                        return new RecipientOperator(new InputDecryptor() {
                            @Override
                            public AlgorithmIdentifier getAlgorithmIdentifier() {
                                return contentEncAlg;
                            }

                            @Override
                            public InputStream getInputStream(InputStream in) {
                                try {
                                    byte[] all = in.readAllBytes();
                                    Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC");
                                    // IV 在信封的内容加密算法参数里（BC 的 CBC 参数即 OCTET STRING IV）
                                    java.security.AlgorithmParameters params =
                                            java.security.AlgorithmParameters.getInstance("SM4", "BC");
                                    params.init(contentEncAlg.getParameters().toASN1Primitive().getEncoded());
                                    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "SM4"), params);
                                    return new ByteArrayInputStream(cipher.doFinal(all));
                                } catch (Exception e) {
                                    throw new IllegalStateException("SM4 内容解密失败", e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        throw new CMSException("SM2 解封 CEK 失败: " + e.getMessage(), e);
                    }
                }
            });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("国密信封开拆失败（SM2+SM4）", e);
        }
    }

    /** 自定义 SM2 密钥封装器：SM2Engine 用收件人公钥加密 CEK（国密信封标准方式）。 */
    private static AsymmetricKeyWrapper sm2Wrapper(ECPublicKeyParameters pub) {
        return new AsymmetricKeyWrapper(new AlgorithmIdentifier(SM2_KEM_OID)) {
            @Override
            public byte[] generateWrappedKey(GenericKey key) throws OperatorException {
                try {
                    SM2Engine engine = new SM2Engine();
                    engine.init(true, new ParametersWithRandom(pub, new SecureRandom()));
                    byte[] in = (byte[]) key.getRepresentation();
                    return engine.processBlock(in, 0, in.length);
                } catch (InvalidCipherTextException e) {
                    throw new OperatorException("SM2 封装 CEK 失败: " + e.getMessage(), e);
                }
            }
        };
    }

    private static org.bouncycastle.cms.SignerInfoGenerator signerInfo(KeyPair signer, X509Certificate signerCert)
            throws Exception {
        return signerInfo(signer, signerCert, "SHA256withRSA");
    }

    /** 按指定签名算法构造 SignerInfoGenerator（RSA 版 SHA256withRSA，国密版 SM3withSM2）。 */
    private static org.bouncycastle.cms.SignerInfoGenerator signerInfo(KeyPair signer, X509Certificate signerCert,
            String signatureAlgorithm) throws Exception {
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC")
                .build(signer.getPrivate());
        JcaDigestCalculatorProviderBuilder digestProvider =
                new JcaDigestCalculatorProviderBuilder().setProvider("BC");
        return new JcaSignerInfoGeneratorBuilder(digestProvider.build())
                .build(contentSigner, signerCert);
    }

    private static boolean verifySignerInfos(java.security.cert.X509Certificate signerCert,
            SignerInformationStore store) {
        return verifySignerInfos(signerCert.getPublicKey(), store);
    }

    private static boolean verifySignerInfos(PublicKey signerPub, SignerInformationStore store) {
        try {
            Collection<SignerInformation> signers = store.getSigners();
            for (SignerInformation signer : signers) {
                if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerPub))) {
                    return false;
                }
            }
            return !signers.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 演示入口。 */
    public static void demo() {
        // 签名者：自签名证书（仅用于携带公钥；真实场景用 CA 签发的证书）
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate signerCert = CertificateDemo.selfSignedCa(signer, new X500Name("CN=CMS Signer, O=Study"));

        String msg = "CMS 演示：PKCS#7 签名（attach/detach）与数字信封（EnvelopedData）";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        System.out.println("原始内容: " + msg + "（" + data.length + " 字节）");
        System.out.println();

        // 1) attach 附件签名
        byte[] attached = signAttached(signer, signerCert, data);
        System.out.println("1) PKCS#7 附件签名（attach）: " + attached.length + " 字节（原文内嵌其中）");
        System.out.println("   验证 + 内嵌原文一致: " + verifyAttached(signerCert, attached, data));
        System.out.println();

        // 2) detach 分离签名
        byte[] detached = signDetached(signer, signerCert, data);
        System.out.println("2) PKCS#7 分离签名（detach）: " + detached.length + " 字节（不含原文，需另传原文）");
        System.out.println("   带原文验证: " + verifyDetached(signerCert.getPublicKey(), data, detached));
        System.out.println("   篡改原文验证: " + verifyDetached(signerCert.getPublicKey(),
                (msg + "!").getBytes(StandardCharsets.UTF_8), detached));
        System.out.println();

        // 3) 数字信封
        KeyPair recipient = CertificateDemo.generateKeyPair();
        X509Certificate recipientCert = CertificateDemo.selfSignedCa(recipient, new X500Name("CN=Envelope Recipient, O=Study"));
        byte[] enveloped = envelop(recipientCert, data);
        byte[] opened = openEnvelope(recipient.getPrivate(), enveloped);
        System.out.println("3) 数字信封（EnvelopedData）: 信封 " + enveloped.length + " 字节");
        System.out.println("   信封前 16 字节: " + HEX.formatHex(java.util.Arrays.copyOf(enveloped, 16)) + "...");
        System.out.println("   收件人私钥解封一致: " + new String(opened, StandardCharsets.UTF_8).equals(msg));
        try {
            KeyPair wrong = CertificateDemo.generateKeyPair();
            openEnvelope(wrong.getPrivate(), enveloped);
            System.out.println("   错误私钥解封: 未拦截（异常！）");
        } catch (IllegalStateException e) {
            System.out.println("   错误私钥解封: 已拒绝（没有对应私钥拿不到对称密钥）");
        }
        System.out.println();

        // 4) PEM 导出 + openssl 命令行验证
        pemExportDemo(signer, signerCert, recipient, recipientCert, msg, data, attached, detached, enveloped);

        // 6) 国密版：SM2 + SM3 + SM4 构造 SignedData/EnvelopedData
        gmDemo();
    }

    /**
     * 国密版演示：SM2+SM3+SM4 构造 SignedData/EnvelopedData，对照 GmDemo 的手工信封。
     *
     * <p>对照关系：与 {@code GmDemo} 是同一套算法（SM2 加密 CEK、SM4 加密数据、SM3 摘要、
     * SM2 签名），但 GmDemo 是自定格式手工拼装（双方必须约定字段顺序），这里是标准 CMS 的
     * ASN.1 结构（SignedData/EnvelopedData）——可互操作、可带证书链、天然支持多收件人。
     */
    private static void gmDemo() {
        System.out.println("6) 国密版：SM2 + SM3 + SM4 构造 SignedData / EnvelopedData");

        KeyPair gmSigner = gmKeyPair();
        X509Certificate gmCert = gmSelfSignedCert(gmSigner, new X500Name("CN=GM CMS Signer, O=Study"));
        String gmMsg = "国密 CMS：SM3withSM2 签名 + SM2/SM4 数字信封（GM/T 0010 形态）";
        byte[] gmData = gmMsg.getBytes(StandardCharsets.UTF_8);

        // 6.1) 国密 SignedData（attach / detach）
        byte[] gmAttached = gmSignAttached(gmSigner, gmCert, gmData);
        byte[] gmDetached = gmSignDetached(gmSigner, gmCert, gmData);
        System.out.println("   SignedData（SM3withSM2）: attach " + gmAttached.length + " 字节 / detach "
                + gmDetached.length + " 字节");
        System.out.println("   attach 验签 + 内嵌原文一致: " + gmVerifyAttached(gmCert, gmAttached, gmData));
        System.out.println("   detach 带原文验签: " + gmVerifyDetached(gmSigner.getPublic(), gmData, gmDetached));
        System.out.println("   detach 篡改原文: " + gmVerifyDetached(gmSigner.getPublic(),
                (gmMsg + "!").getBytes(StandardCharsets.UTF_8), gmDetached));
        System.out.println();

        // 6.2) 国密 EnvelopedData（SM4-CBC 内容加密 + SM2 封装 CEK）
        KeyPair gmRecipient = gmKeyPair();
        byte[] gmEnv = gmEnvelop(gmRecipient.getPublic(), gmData);
        byte[] gmOpened = gmOpenEnvelope(gmRecipient.getPrivate(), gmEnv);
        System.out.println("   EnvelopedData: 信封 " + gmEnv.length + " 字节");
        System.out.println("   收件人开拆一致: " + new String(gmOpened, StandardCharsets.UTF_8).equals(gmMsg));
        try {
            gmOpenEnvelope(gmKeyPair().getPrivate(), gmEnv);
            System.out.println("   错误私钥开拆: 未拦截（异常！）");
        } catch (IllegalStateException e) {
            System.out.println("   错误私钥开拆: 已拒绝（SM3 校验失败，拿不到 CEK）");
        }
        System.out.println("   对照 GmDemo: 同一套 SM2/SM3/SM4，这里走标准 CMS ASN.1 结构"
                + "（可互操作、可带证书链、多收件人），GmDemo 是自定格式手工拼装");
        System.out.println();
    }

    /**
     * PEM 导出演示：把 attach/detach 签名与信封导出成 .p7m/.p7s/.p7e（PEM 格式），
     * 连同签名者证书、收件人证书/私钥写到 target/cms/，打印并自动执行 openssl 命令验证
     * （环境无 openssl 时仅打印命令供手动执行）。
     */
    private static void pemExportDemo(KeyPair signer, X509Certificate signerCert,
            KeyPair recipient, X509Certificate recipientCert, String msg, byte[] data,
            byte[] attached, byte[] detached, byte[] enveloped) {
        java.nio.file.Path dir = java.nio.file.Path.of("target", "cms");
        try {
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("original.txt"), msg, StandardCharsets.UTF_8);
            writePemFile(dir, "signed-attached.p7m", "PKCS7", attached);
            writePemFile(dir, "signed-detached.p7s", "PKCS7", detached);
            writePemFile(dir, "enveloped.p7e", "PKCS7", enveloped);
            writePemFile(dir, "signer.pem", "CERTIFICATE", signerCert.getEncoded());
            writePemFile(dir, "recipient.pem", "CERTIFICATE", recipientCert.getEncoded());
            writePemFile(dir, "recipient-key.pem", "PRIVATE KEY", recipient.getPrivate().getEncoded());

            System.out.println("4) PEM 导出（目录 target/cms/）:");
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                files.sorted().forEach(f -> System.out.println("   " + f.getFileName() + "  (" + sizeOf(f) + " 字节)"));
            }
            System.out.println();

            // openssl 验证命令（自签名证书 → -noverify 跳过证书链检查，签名本身仍会被验证）
            String attachCmd = "openssl smime -verify -in target/cms/signed-attached.p7m -inform PEM"
                    + " -certfile target/cms/signer.pem -noverify -out /dev/null";
            String detachCmd = "openssl smime -verify -in target/cms/signed-detached.p7s -inform PEM"
                    + " -content target/cms/original.txt -certfile target/cms/signer.pem -noverify -out /dev/null";
            String envelopCmd = "openssl smime -decrypt -in target/cms/enveloped.p7e -inform PEM"
                    + " -recip target/cms/recipient.pem -inkey target/cms/recipient-key.pem"
                    + " -out target/cms/decrypted.txt";

            System.out.println("5) openssl 命令行验证:");
            runOpenssl("attach 验签（内嵌原文，无需 -content）", attachCmd);
            runOpenssl("detach 验签（需 -content 指定原文）", detachCmd);
            runOpenssl("信封解密（收件人私钥）", envelopCmd);

            // 解密结果与原文比较
            java.nio.file.Path decrypted = dir.resolve("decrypted.txt");
            if (java.nio.file.Files.exists(decrypted)) {
                String opened = java.nio.file.Files.readString(decrypted, StandardCharsets.UTF_8);
                System.out.println("   openssl 解密内容与原文一致: " + opened.equals(msg));
            }
            System.out.println();
        } catch (Exception e) {
            throw new IllegalStateException("PEM 导出演示失败", e);
        }
    }

    private static long sizeOf(java.nio.file.Path f) {
        try {
            return java.nio.file.Files.size(f);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 执行一条 openssl 命令并打印结果；openssl 不可用或失败时打印命令供手动执行。 */
    private static void runOpenssl(String label, String command) {
        System.out.println("   " + label + ":");
        try {
            Process process = new ProcessBuilder("bash", "-c", command)
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int code = process.waitFor();
            if (code == 0) {
                System.out.println("     成功（exit=0）" + (output.isEmpty() ? "" : " · " + output));
            } else {
                System.out.println("     失败（exit=" + code + "）: " + output);
                System.out.println("     手动执行: " + command);
            }
        } catch (Exception e) {
            System.out.println("     无法执行（openssl 不可用）: " + e.getMessage());
            System.out.println("     手动执行: " + command);
        }
    }
}
