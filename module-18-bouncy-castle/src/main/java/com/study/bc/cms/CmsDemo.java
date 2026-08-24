package com.study.bc.cms;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HexFormat;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import com.study.bc.BcSupport;
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

    private CmsDemo() {
    }

    static {
        BcSupport.register(); // CMS JCA 桥接需 BC provider
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

    private static org.bouncycastle.cms.SignerInfoGenerator signerInfo(KeyPair signer, X509Certificate signerCert)
            throws Exception {
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC")
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
