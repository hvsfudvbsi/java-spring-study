package com.study.bc.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CertificateDemoTest {

    private static X509Certificate caCert;
    private static KeyPair caKey;
    private static X500Name caName;
    private static X509Certificate serverCert;

    @BeforeAll
    static void setUp() {
        caKey = CertificateDemo.generateKeyPair();
        caName = new X500Name("CN=Test CA");
        caCert = CertificateDemo.selfSignedCa(caKey, caName);
        KeyPair serverKey = CertificateDemo.generateKeyPair();
        serverCert = CertificateDemo.issueServer(caName, caKey, serverKey,
                new X500Name("CN=server.test.local"), "server.test.local");
    }

    @Test
    @DisplayName("CA 根证书自签名：Subject=Issuer，自身公钥验签通过")
    void caSelfSigned() {
        assertEquals("CN=Test CA", caCert.getSubjectX500Principal().getName());
        assertEquals(caCert.getSubjectX500Principal(), caCert.getIssuerX500Principal());
        assertTrue(CertificateDemo.verifySelfSigned(caCert));
    }

    @Test
    @DisplayName("服务器证书由 CA 签发：Issuer=CA，CA 公钥验签通过，SAN 域名正确")
    void serverIssuedByCa() {
        assertEquals("CN=Test CA", serverCert.getIssuerX500Principal().getName());
        assertTrue(CertificateDemo.verifySignature(serverCert, caKey.getPublic()));
        assertEquals(List.of("server.test.local"), CertificateDemo.subjectAltNames(serverCert));
    }

    @Test
    @DisplayName("信任链验证：CA 签发的证书链通过，无关 CA 拒绝")
    void chainValidation() {
        assertTrue(CertificateDemo.verifyChain(serverCert, caCert));
        KeyPair evil = CertificateDemo.generateKeyPair();
        X509Certificate unrelatedCa = CertificateDemo.selfSignedCa(evil, new X500Name("CN=Evil CA"));
        assertFalse(CertificateDemo.verifyChain(serverCert, unrelatedCa));
    }

    @Test
    @DisplayName("有效期检查：当前时间有效通过，过期证书被拒绝")
    void validityCheck() {
        CertificateDemo.checkValidity(serverCert); // 当前时间有效，不抛

        // 过期证书应被拒绝
        long now = System.currentTimeMillis();
        X509Certificate expired = CertificateDemo.issue(
                new X500Name("CN=x"), new X500Name("CN=x"), caKey, caKey.getPublic(),
                BigInteger.ONE, new Date(now - 2L * 86_400_000L), new Date(now - 86_400_000L), false, null);
        assertThrows(IllegalStateException.class, () -> CertificateDemo.checkValidity(expired));
    }

    @Test
    @DisplayName("无关公钥验签失败：不是 CA 私钥签的必然拒绝")
    void tamperedPublicKeyFails() {
        // 用无关密钥对的公钥去验证服务器证书的签名（CA 私钥签的）必然失败
        KeyPair unrelated = CertificateDemo.generateKeyPair();
        assertThrows(Exception.class, () -> serverCert.verify(unrelated.getPublic()));
    }
}
