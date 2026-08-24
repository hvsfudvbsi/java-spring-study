package com.study.bc.p10;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import com.study.bc.BcSupport;
import com.study.bc.cert.CertificateDemo;

/**
 * PKCS#10（P10 / CSR，证书签名请求 Certificate Signing Request）演示。
 *
 * <p>真实 PKI 的「申请证书」一环：
 * <ol>
 *   <li>申请人自己生成密钥对，<b>私钥永不外传</b>；</li>
 *   <li>用私钥对「Subject DN + 公钥 + 扩展（如 SAN 域名）」签名生成 CSR（P10）——
 *   证明「我确实持有这把私钥」；</li>
 *   <li>把 CSR 交给 CA；CA 用 CSR 内嵌公钥验签（{@link #verifyCsr}），确认申请人持有私钥；</li>
 *   <li>CA 用 CSR 的 Subject / 公钥（及请求的扩展）签发 X.509 证书（{@link #issueFromCsr}）返回给申请人。</li>
 * </ol>
 *
 * <p>适用场景：证书申请自动化（如 ACME/内部 CA 门户）、企业 PKI 的签发流程；
 * 对应 openssl 命令：{@code openssl req -new -key key.pem -out csr.pem} 与
 * {@code openssl x509 -req -in csr.pem -CA ca.pem -CAkey ca.key -out cert.pem}。
 */
public final class CsrDemo {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrDemo() {
    }

    static {
        BcSupport.register(); // JCA 桥接需 BC provider
    }

    /** 构建 PKCS#10 CSR：用私钥对「Subject + 公钥 + SAN 域名扩展」签名。 */
    public static PKCS10CertificationRequest buildCsr(KeyPair keyPair, String subjectDn, String sanDns) {
        try {
            JcaPKCS10CertificationRequestBuilder builder =
                    new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn), keyPair.getPublic());
            if (sanDns != null) {
                // CSR 的扩展放在 extensionRequest 属性（PKCS#9 OID 1.2.840.113549.1.9.14）里
                GeneralNames names = new GeneralNames(new GeneralName(GeneralName.dNSName, sanDns));
                ExtensionsGenerator extensions = new ExtensionsGenerator();
                extensions.addExtension(Extension.subjectAlternativeName, false, names);
                builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate());
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider("BC").build(keyPair.getPrivate());
            return builder.build(signer);
        } catch (Exception e) {
            throw new IllegalStateException("CSR 构建失败", e);
        }
    }

    /** 验证 CSR 签名：用 CSR 内嵌公钥验签，确认申请人确实持有对应私钥。 */
    public static boolean verifyCsr(PKCS10CertificationRequest csr, PublicKey publicKey) {
        try {
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                    .setProvider("BC").build(publicKey);
            return csr.isSignatureValid(verifier);
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取 CSR 的 Subject DN。 */
    public static X500Name subjectOf(PKCS10CertificationRequest csr) {
        return csr.getSubject();
    }

    /** 读取 CSR 内嵌公钥（申请人公钥）。 */
    public static PublicKey publicKeyOf(PKCS10CertificationRequest csr) {
        try {
            return new JcaPKCS10CertificationRequest(csr).getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("CSR 公钥读取失败", e);
        }
    }

    /** 读取 CSR 请求的 SAN 域名扩展（extensionRequest 属性）。 */
    public static List<String> sansOf(PKCS10CertificationRequest csr) {
        Extensions extensions = csr.getRequestedExtensions();
        if (extensions == null) {
            return List.of();
        }
        Extension san = extensions.getExtension(Extension.subjectAlternativeName);
        if (san == null) {
            return List.of();
        }
        GeneralNames names = GeneralNames.getInstance(san.getParsedValue());
        List<String> dns = new ArrayList<>();
        for (GeneralName name : names.getNames()) {
            if (name.getTagNo() == GeneralName.dNSName) {
                dns.add(name.getName().toString());
            }
        }
        return dns;
    }

    /** CA 基于 CSR 签发叶证书（CA=false，有效期 1 年，沿用请求的 SAN 扩展）。 */
    public static X509Certificate issueFromCsr(X500Name caName, KeyPair caKey, PKCS10CertificationRequest csr) {
        return issueFromCsr(caName, caKey, csr, false, 365L);
    }

    /** 根 CA 基于 CSR 签发中间 CA 证书（BasicConstraints: CA=true + keyCertSign，有效期 5 年）。 */
    public static X509Certificate issueIntermediateFromCsr(X500Name rootName, KeyPair rootKey,
            PKCS10CertificationRequest intermediateCsr) {
        return issueFromCsr(rootName, rootKey, intermediateCsr, true, 1825L);
    }

    /** 二级 CA 链验证：叶证书 → 中间 CA → 根 CA（PKIX 完整链，需所有证书均有效）。 */
    public static boolean verifyChain(X509Certificate leaf, X509Certificate intermediate, X509Certificate root) {
        return CertificateDemo.verifyChain(java.util.List.of(leaf, intermediate), root);
    }

    /** 用签发者私钥基于 CSR 签发证书（内部通用）。 */
    private static X509Certificate issueFromCsr(X500Name issuer, KeyPair issuerKey,
            PKCS10CertificationRequest csr, boolean isCa, long validityDays) {
        try {
            long now = System.currentTimeMillis();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, new BigInteger(64, RANDOM),
                    new Date(now - 86_400_000L), new Date(now + validityDays * 86_400_000L),
                    csr.getSubject(), publicKeyOf(csr));
            // 沿用 CSR 请求的 SAN 域名扩展（真实 CA 会按策略校验/改写）
            Extensions extensions = csr.getRequestedExtensions();
            if (extensions != null && extensions.getExtension(Extension.subjectAlternativeName) != null) {
                builder.addExtension(Extension.subjectAlternativeName, false,
                        extensions.getExtension(Extension.subjectAlternativeName).getParsedValue());
            }
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));
            if (isCa) {
                // CA 证书必须能签证书：keyCertSign + cRLSign（RFC 5280）
                builder.addExtension(Extension.keyUsage, true, new org.bouncycastle.asn1.x509.KeyUsage(
                        org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                                | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.getPrivate());
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("基于 CSR 签发证书失败", e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        // 1. 申请人自己生成密钥对（私钥不出本机）
        KeyPair applicant = CertificateDemo.generateKeyPair();
        String subjectDn = "CN=csr.example.com, O=Study";
        System.out.println("1) 申请人生成密钥对（RSA-2048），私钥保存在本机");
        System.out.println("   申请 Subject: " + subjectDn);
        System.out.println();

        // 2. 构建 CSR（P10）：Subject + 公钥 + SAN 域名，私钥签名
        PKCS10CertificationRequest csr = buildCsr(applicant, subjectDn, "csr.example.com");
        int csrLen;
        try {
            csrLen = csr.getEncoded().length;
        } catch (Exception e) {
            csrLen = -1;
        }
        System.out.println("2) 构建 PKCS#10 CSR: " + csrLen + " 字节");
        System.out.println("   签发算法: " + csr.getSignatureAlgorithm().getAlgorithm().getId());
        System.out.println("   Subject: " + subjectOf(csr));
        System.out.println("   请求的 SAN 域名: " + sansOf(csr));
        System.out.println();

        // 3. CA 验签：确认申请人持有私钥（用 CSR 内嵌公钥验签）
        System.out.println("3) CA 验签 CSR（用 CSR 内嵌公钥）: " + verifyCsr(csr, publicKeyOf(csr)));
        System.out.println("   换一把公钥验签: "
                + verifyCsr(csr, CertificateDemo.generateKeyPair().getPublic()) + "（应为 false）");
        System.out.println();

        // 4. CA 基于 CSR 签发证书
        KeyPair caKey = CertificateDemo.generateKeyPair();
        X500Name caName = new X500Name("CN=Study PKI Root CA, O=Study");
        X509Certificate cert = issueFromCsr(caName, caKey, csr);
        System.out.println("4) CA 基于 CSR 签发证书:");
        System.out.println("   Subject: " + cert.getSubjectX500Principal());
        System.out.println("   Issuer : " + cert.getIssuerX500Principal());
        System.out.println("   SAN    : " + CertificateDemo.subjectAltNames(cert));
        System.out.println("   证书公钥与 CSR 公钥一致: "
                + java.util.Arrays.equals(cert.getPublicKey().getEncoded(), publicKeyOf(csr).getEncoded()));
        System.out.println("   CA 验签证书: " + CertificateDemo.verifySignature(cert, caKey.getPublic()));
        System.out.println();

        // 5) 二级 CA 链：根 CA → 中间 CA → 叶证书（全链路基于 CSR 签发）
        twoTierCaDemo(csr);
    }

    /** 二级 CA 签发流程演示：根自签名 → 中间 CA（基于自身 CSR）→ 叶证书（基于申请人 CSR）→ 完整链验证。 */
    private static void twoTierCaDemo(PKCS10CertificationRequest leafCsr) {
        System.out.println("5) 二级 CA 链（根 CA → 中间 CA → 基于 CSR 签发叶证书）:");

        // ① 根 CA 自签名（离线保存，私钥最安全）
        KeyPair rootKey = CertificateDemo.generateKeyPair();
        X500Name rootName = new X500Name("CN=Study Two-Tier Root CA, O=Study");
        X509Certificate rootCert = CertificateDemo.selfSignedCa(rootKey, rootName);
        System.out.println("   根 CA 自签名: " + rootCert.getSubjectX500Principal());

        // ② 中间 CA：先构建自己的 CSR，再由根 CA 签发（CA=true + keyCertSign，5 年）
        KeyPair intermediateKey = CertificateDemo.generateKeyPair();
        PKCS10CertificationRequest intermediateCsr =
                buildCsr(intermediateKey, "CN=Study Intermediate CA, O=Study", null);
        X509Certificate intermediateCert = issueIntermediateFromCsr(rootName, rootKey, intermediateCsr);
        System.out.println("   中间 CA（根签发, CA=true）: " + intermediateCert.getSubjectX500Principal());

        // ③ 叶证书：中间 CA 基于申请人 CSR 签发（1 年）；Issuer 复用 CSR 的 Subject 对象避免 DN 编码往返
        X509Certificate leafCert = issueFromCsr(intermediateCsr.getSubject(), intermediateKey, leafCsr);
        System.out.println("   叶证书（中间 CA 签发）: " + leafCert.getSubjectX500Principal()
                + ", SAN=" + CertificateDemo.subjectAltNames(leafCert));
        System.out.println();

        // ④ 逐层验签 + PKIX 完整链
        System.out.println("   中间 CA 用根公钥验签: " + CertificateDemo.verifySignature(intermediateCert, rootKey.getPublic()));
        System.out.println("   叶证书用中间 CA 公钥验签: " + CertificateDemo.verifySignature(leafCert, intermediateKey.getPublic()));
        System.out.println("   PKIX 完整链验证（叶→中间→根）: " + verifyChain(leafCert, intermediateCert, rootCert));

        // ⑤ 反例：无关根无法通过完整链验证
        X509Certificate unrelatedRoot = CertificateDemo.selfSignedCa(
                CertificateDemo.generateKeyPair(), new X500Name("CN=Evil Root CA, O=Study"));
        System.out.println("   无关根验证（应为 false）: " + verifyChain(leafCert, intermediateCert, unrelatedRoot));
        System.out.println();
    }
}
