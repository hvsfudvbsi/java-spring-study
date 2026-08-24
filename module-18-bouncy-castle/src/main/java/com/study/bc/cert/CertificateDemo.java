package com.study.bc.cert;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.study.bc.BcSupport;

/**
 * X.509 证书演示：自签名根证书（CA）签发服务器证书，并验证信任链。
 *
 * <p>流程：
 * <ol>
 *   <li>生成 CA 密钥对 → 签发一张自签名根证书（BasicConstraints: CA=true）；</li>
 *   <li>生成服务器密钥对 → 用 CA 私钥签发服务器证书（SAN 域名扩展）；</li>
 *   <li>验证：签名校验（公钥验签）、有效期检查、CertPathValidator 信任链验证；</li>
 *   <li>反例：篡改公钥的证书验签失败、过期证书有效期检查失败。</li>
 * </ol>
 *
 * <p>适用场景：HTTPS 服务器证书、代码签名、S/MIME 邮件、企业内部 PKI
 * （CA 签发 + 信任链验证）。
 */
public final class CertificateDemo {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CertificateDemo() {
    }

    static {
        BcSupport.register(); // JcaX509CertificateConverter 经 JCE 转换
    }

    /** 生成 RSA-2048 密钥对。 */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, RANDOM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥生成失败", e);
        }
    }

    /** 签发一张 X.509 证书：issuerKey 签发的 Subject=subjectName、公钥=subjectKey 的证书。 */
    public static X509Certificate issue(X500Name issuer, X500Name subject, KeyPair issuerKey,
            java.security.PublicKey subjectKey, BigInteger serial, Date notBefore, Date notAfter,
            boolean isCa, String sanDns) {
        try {
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, subjectKey);
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));
            if (sanDns != null) {
                GeneralNames names = new GeneralNames(new GeneralName(GeneralName.dNSName, sanDns));
                builder.addExtension(Extension.subjectAlternativeName, false, names);
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("证书签发失败", e);
        }
    }

    /** 自签名根证书：签发者 = 持有者，有效期 10 年。 */
    public static X509Certificate selfSignedCa(KeyPair caKey, X500Name name) {
        long now = System.currentTimeMillis();
        return issue(name, name, caKey, caKey.getPublic(), new BigInteger(64, RANDOM),
                new Date(now - 86_400_000L), new Date(now + 3650L * 86_400_000L), true, null);
    }

    /** 用 CA 签发一张服务器证书（有效期 1 年，带 SAN 域名）。issuerName 必须是 CA 证书的原始 Subject 名。 */
    public static X509Certificate issueServer(X500Name issuerName, KeyPair caKey,
            KeyPair serverKey, X500Name serverName, String dns) {
        long now = System.currentTimeMillis();
        return issue(issuerName, serverName, caKey, serverKey.getPublic(), new BigInteger(64, RANDOM),
                new Date(now - 86_400_000L), new Date(now + 365L * 86_400_000L), false, dns);
    }

    /** 用指定公钥（签发者的公钥）验证证书签名，返回是否通过。 */
    public static boolean verifySignature(X509Certificate cert, java.security.PublicKey signerKey) {
        try {
            cert.verify(signerKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 自签名证书用自身公钥验签（仅适用于 CA 根证书）。 */
    public static boolean verifySelfSigned(X509Certificate cert) {
        return verifySignature(cert, cert.getPublicKey());
    }

    /** 验证证书当前时间是否在有效期内；不在则抛异常。 */
    public static void checkValidity(X509Certificate cert) {
        try {
            cert.checkValidity();
        } catch (Exception e) {
            throw new IllegalStateException("证书不在有效期内: " + e.getMessage(), e);
        }
    }

    /** 信任链验证：leaf 必须由 trustAnchor 直接签发（单级链演示）。 */
    public static boolean verifyChain(X509Certificate leaf, X509Certificate trustAnchor) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            CertPath path = cf.generateCertPath(List.of(leaf));
            PKIXParameters params = new PKIXParameters(Set.of(new TrustAnchor(trustAnchor, null)));
            params.setRevocationEnabled(false); // 演示环境不做 OCSP/CRL
            java.security.cert.CertPathValidator validator =
                    java.security.cert.CertPathValidator.getInstance("PKIX");
            validator.validate(path, params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取证书 SAN（Subject Alternative Name）域名列表。 */
    public static List<String> subjectAltNames(X509Certificate cert) {
        try {
            java.util.Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                return List.of();
            }
            return sans.stream()
                    .filter(san -> san.size() >= 2 && (Integer) san.get(0) == GeneralName.dNSName)
                    .map(san -> (String) san.get(1))
                    .toList();
        } catch (CertificateParsingException e) {
            return List.of();
        }
    }

    /** 演示入口。 */
    public static void demo() {
        // 1. CA 自签名根证书
        KeyPair caKey = generateKeyPair();
        X500Name caName = new X500Name("CN=Study Root CA, O=Study");
        X509Certificate caCert = selfSignedCa(caKey, caName);
        System.out.println("1) CA 根证书签发: " + caCert.getSubjectX500Principal());
        System.out.println("   有效期: " + caCert.getNotBefore() + " ~ " + caCert.getNotAfter());
        System.out.println("   自签名验签: " + verifySelfSigned(caCert));
        System.out.println();

        // 2. CA 签发服务器证书
        KeyPair serverKey = generateKeyPair();
        X500Name serverName = new X500Name("CN=study.example.com, O=Study");
        X509Certificate serverCert = issueServer(caName, caKey, serverKey, serverName, "study.example.com");
        System.out.println("2) CA 签发服务器证书: " + serverCert.getSubjectX500Principal());
        System.out.println("   签发者(Issuer): " + serverCert.getIssuerX500Principal());
        System.out.println("   序列号: " + serverCert.getSerialNumber());
        System.out.println("   SAN 域名: " + subjectAltNames(serverCert));
        System.out.println("   服务器证书验签(用 CA 公钥): " + verifySignature(serverCert, caKey.getPublic()));
        System.out.println();

        // 3. 信任链验证
        System.out.println("3) 信任链验证 (服务器证书 ← CA 根证书): " + verifyChain(serverCert, caCert));
        KeyPair otherCa = generateKeyPair();
        X509Certificate unrelatedCa = selfSignedCa(otherCa, new X500Name("CN=Evil Root CA"));
        System.out.println("   无关 CA 信任: " + verifyChain(serverCert, unrelatedCa) + "（应为 false）");
        System.out.println();

        // 4. 有效期检查
        checkValidity(serverCert);
        System.out.println("4) 有效期检查: 当前时间有效 (通过)");
        long now = System.currentTimeMillis();
        X509Certificate expired = issue(serverName, serverName, serverKey, serverKey.getPublic(),
                BigInteger.ONE, new Date(now - 2L * 86_400_000L), new Date(now - 86_400_000L), false, null);
        try {
            checkValidity(expired);
            System.out.println("   过期证书: 未拦截（异常！）");
        } catch (IllegalStateException e) {
            System.out.println("   过期证书: 已拒绝（" + e.getMessage() + "）");
        }
        System.out.println();
    }
}
