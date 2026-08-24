package com.study.bc.p10;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.bc.cert.CertificateDemo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrDemoTest {

    private static final String SUBJECT_DN = "CN=csr.example.com, O=Study";

    private static KeyPair keyPair() {
        return CertificateDemo.generateKeyPair();
    }

    @Test
    @DisplayName("CSR 构建：Subject 与公钥正确，SAN 域名扩展被记录")
    void buildCsrReadsBack() {
        KeyPair applicant = keyPair();
        PKCS10CertificationRequest csr = CsrDemo.buildCsr(applicant, SUBJECT_DN, "csr.example.com");
        assertEquals(new X500Name(SUBJECT_DN), CsrDemo.subjectOf(csr));
        assertTrue(java.util.Arrays.equals(applicant.getPublic().getEncoded(),
                CsrDemo.publicKeyOf(csr).getEncoded()));
        assertEquals(List.of("csr.example.com"), CsrDemo.sansOf(csr));
    }

    @Test
    @DisplayName("CSR 无 SAN 扩展时读取返回空列表")
    void buildCsrWithoutSan() {
        PKCS10CertificationRequest csr = CsrDemo.buildCsr(keyPair(), SUBJECT_DN, null);
        assertEquals(List.of(), CsrDemo.sansOf(csr));
    }

    @Test
    @DisplayName("CSR 验签：申请人公钥验签通过，他人公钥验签失败")
    void verifyCsr() {
        KeyPair applicant = keyPair();
        PKCS10CertificationRequest csr = CsrDemo.buildCsr(applicant, SUBJECT_DN, "csr.example.com");
        assertTrue(CsrDemo.verifyCsr(csr, applicant.getPublic()));
        assertFalse(CsrDemo.verifyCsr(csr, keyPair().getPublic()));
    }

    @Test
    @DisplayName("CSR 篡改检测：CSR 字节被改后验签失败（签名覆盖全部内容）")
    void tamperedCsrFails() throws Exception {
        KeyPair applicant = keyPair();
        PKCS10CertificationRequest csr = CsrDemo.buildCsr(applicant, SUBJECT_DN, "csr.example.com");
        byte[] encoded = csr.getEncoded();
        encoded[encoded.length - 1] ^= 0x01;
        PKCS10CertificationRequest tampered = new PKCS10CertificationRequest(encoded);
        assertFalse(CsrDemo.verifyCsr(tampered, applicant.getPublic()));
    }

    @Test
    @DisplayName("CA 基于 CSR 签发证书：Subject/公钥沿用 CSR，SAN 扩展带入证书，CA 验签通过")
    void issueFromCsr() {
        KeyPair applicant = keyPair();
        PKCS10CertificationRequest csr = CsrDemo.buildCsr(applicant, SUBJECT_DN, "csr.example.com");
        KeyPair caKey = keyPair();
        X500Name caName = new X500Name("CN=Study PKI Root CA, O=Study");
        X509Certificate cert = CsrDemo.issueFromCsr(caName, caKey, csr);

        assertEquals(new X500Name(SUBJECT_DN), new X500Name(cert.getSubjectX500Principal().getName()));
        assertTrue(java.util.Arrays.equals(applicant.getPublic().getEncoded(), cert.getPublicKey().getEncoded()));
        assertEquals(List.of("csr.example.com"), CertificateDemo.subjectAltNames(cert));
        assertTrue(CertificateDemo.verifySignature(cert, caKey.getPublic()));
        CertificateDemo.checkValidity(cert);
    }

    @Test
    @DisplayName("二级 CA 链：根签发中间 CA（CA=true），中间 CA 基于 CSR 签发叶证书，PKIX 完整链验证通过")
    void twoTierChain() {
        KeyPair rootKey = keyPair();
        X500Name rootName = new X500Name("CN=Two-Tier Root CA, O=Study");
        X509Certificate rootCert = CertificateDemo.selfSignedCa(rootKey, rootName);

        // 中间 CA：先构建自身 CSR，根 CA 签发（CA=true + keyCertSign）
        KeyPair intermediateKey = keyPair();
        PKCS10CertificationRequest intermediateCsr =
                CsrDemo.buildCsr(intermediateKey, "CN=Two-Tier Intermediate CA, O=Study", null);
        X509Certificate intermediateCert = CsrDemo.issueIntermediateFromCsr(rootName, rootKey, intermediateCsr);
        assertTrue(intermediateCert.getBasicConstraints() >= 0); // CA=true
        assertEquals(new X500Name("CN=Two-Tier Intermediate CA, O=Study"),
                new X500Name(intermediateCert.getSubjectX500Principal().getName()));
        assertTrue(CertificateDemo.verifySignature(intermediateCert, rootKey.getPublic()));
        assertEquals(new X500Name("CN=Two-Tier Root CA, O=Study"),
                new X500Name(intermediateCert.getIssuerX500Principal().getName()));

        // 叶证书：中间 CA 基于申请人 CSR 签发
        KeyPair applicant = keyPair();
        PKCS10CertificationRequest leafCsr = CsrDemo.buildCsr(applicant, SUBJECT_DN, "csr.example.com");
        X509Certificate leaf = CsrDemo.issueFromCsr(intermediateCsr.getSubject(), intermediateKey, leafCsr);
        assertTrue(leaf.getBasicConstraints() == -1); // 叶证书非 CA
        assertTrue(CertificateDemo.verifySignature(leaf, intermediateKey.getPublic()));
        assertEquals(new X500Name("CN=Two-Tier Intermediate CA, O=Study"),
                new X500Name(leaf.getIssuerX500Principal().getName()));
        assertTrue(java.util.Arrays.equals(applicant.getPublic().getEncoded(), leaf.getPublicKey().getEncoded()));

        // 完整链验证（叶→中间→根）
        assertTrue(CsrDemo.verifyChain(leaf, intermediateCert, rootCert));
        CertificateDemo.checkValidity(leaf);
    }

    @Test
    @DisplayName("二级 CA 链反例：无关根 CA 无法通过完整链验证")
    void twoTierChainWrongRootFails() {
        KeyPair rootKey = keyPair();
        X500Name rootName = new X500Name("CN=Two-Tier Root CA, O=Study");
        X509Certificate rootCert = CertificateDemo.selfSignedCa(rootKey, rootName);
        KeyPair intermediateKey = keyPair();
        PKCS10CertificationRequest intermediateCsr =
                CsrDemo.buildCsr(intermediateKey, "CN=Two-Tier Intermediate CA, O=Study", null);
        X509Certificate intermediateCert = CsrDemo.issueIntermediateFromCsr(rootName, rootKey, intermediateCsr);
        KeyPair applicant = keyPair();
        X509Certificate leaf = CsrDemo.issueFromCsr(intermediateCsr.getSubject(), intermediateKey,
                CsrDemo.buildCsr(applicant, SUBJECT_DN, "csr.example.com"));

        X509Certificate unrelatedRoot = CertificateDemo.selfSignedCa(keyPair(), new X500Name("CN=Evil Root CA"));
        assertFalse(CsrDemo.verifyChain(leaf, intermediateCert, unrelatedRoot));
    }
}
