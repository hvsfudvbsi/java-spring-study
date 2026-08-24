package com.study.bc.cms;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.bc.cert.CertificateDemo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmsDemoTest {

    private static final byte[] DATA = "CMS 测试数据：PKCS#7 签名与数字信封".getBytes(StandardCharsets.UTF_8);

    private static X509Certificate selfSignedCert(KeyPair keyPair, String cn) {
        return CertificateDemo.selfSignedCa(keyPair, new X500Name("CN=" + cn + ", O=Study"));
    }

    @Test
    @DisplayName("CMS 附件签名：签名后验证通过，内嵌内容与原文一致")
    void attachedSignRoundTrip() {
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(signer, "CmsSigner");
        byte[] signed = CmsDemo.signAttached(signer, cert, DATA);
        assertTrue(CmsDemo.verifyAttached(cert, signed, DATA));
    }

    @Test
    @DisplayName("CMS 附件签名：内嵌内容可提取且等于原文（attach 自带内容）")
    void attachedEmbedsContent() throws Exception {
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(signer, "CmsSigner");
        byte[] signed = CmsDemo.signAttached(signer, cert, DATA);
        org.bouncycastle.cms.CMSSignedData cms = new org.bouncycastle.cms.CMSSignedData(signed);
        byte[] embedded = (byte[]) cms.getSignedContent().getContent();
        assertArrayEquals(DATA, embedded);
    }

    @Test
    @DisplayName("CMS 附件签名：篡改签名后验证失败")
    void attachedTamperFails() {
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(signer, "CmsSigner");
        byte[] signed = CmsDemo.signAttached(signer, cert, DATA);
        byte[] tampered = signed.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertFalse(CmsDemo.verifyAttached(cert, tampered, DATA));
    }

    @Test
    @DisplayName("CMS 分离签名：带原文验证通过，篡改原文验证失败，分离签名不含原文")
    void detachedSignRoundTrip() {
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(signer, "CmsSigner");
        byte[] detached = CmsDemo.signDetached(signer, cert, DATA);
        assertTrue(CmsDemo.verifyDetached(cert.getPublicKey(), DATA, detached));
        assertFalse(CmsDemo.verifyDetached(cert.getPublicKey(),
                (new String(DATA, StandardCharsets.UTF_8) + "!").getBytes(StandardCharsets.UTF_8), detached));
        // detach 不含原文：签名比 attach（含内容）短
        byte[] attached = CmsDemo.signAttached(signer, cert, DATA);
        assertTrue(detached.length < attached.length);
    }

    @Test
    @DisplayName("CMS 分离签名：换签名者公钥验证失败（防伪冒）")
    void detachedWrongKeyFails() {
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(signer, "CmsSigner");
        byte[] detached = CmsDemo.signDetached(signer, cert, DATA);
        KeyPair other = CertificateDemo.generateKeyPair();
        assertFalse(CmsDemo.verifyDetached(other.getPublic(), DATA, detached));
    }

    @Test
    @DisplayName("数字信封：收件人私钥解封还原明文")
    void envelopeRoundTrip() {
        KeyPair recipient = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(recipient, "EnvelopeRecipient");
        byte[] enveloped = CmsDemo.envelop(cert, DATA);
        assertArrayEquals(DATA, CmsDemo.openEnvelope(recipient.getPrivate(), enveloped));
    }

    @Test
    @DisplayName("数字信封：错误私钥解封被拒绝（没有对应私钥拿不到对称密钥）")
    void envelopeWrongKeyRejected() {
        KeyPair recipient = CertificateDemo.generateKeyPair();
        X509Certificate cert = selfSignedCert(recipient, "EnvelopeRecipient");
        byte[] enveloped = CmsDemo.envelop(cert, DATA);
        KeyPair wrong = CertificateDemo.generateKeyPair();
        assertThrows(IllegalStateException.class, () -> CmsDemo.openEnvelope(wrong.getPrivate(), enveloped));
    }
}
