package com.study.bc.cms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    @DisplayName("国密 SignedData：SM3withSM2 签名 attach/detach 验证通过，篡改原文拒绝")
    void gmSignedDataRoundTrip() {
        KeyPair signer = CmsDemo.gmKeyPair();
        X509Certificate cert = CmsDemo.gmSelfSignedCert(signer, new X500Name("CN=GM Signer, O=Study"));
        byte[] attached = CmsDemo.gmSignAttached(signer, cert, DATA);
        byte[] detached = CmsDemo.gmSignDetached(signer, cert, DATA);
        // attach：验签 + 内嵌原文一致
        assertTrue(CmsDemo.gmVerifyAttached(cert, attached, DATA));
        // detach：带原文验签通过，篡改原文失败
        assertTrue(CmsDemo.gmVerifyDetached(signer.getPublic(), DATA, detached));
        assertFalse(CmsDemo.gmVerifyDetached(signer.getPublic(),
                (new String(DATA, StandardCharsets.UTF_8) + "!").getBytes(StandardCharsets.UTF_8), detached));
    }

    @Test
    @DisplayName("国密 SignedData：摘要用 SM3（OID 1.2.156.10197.1.401）、签名用 SM3withSM2（OID 1.2.156.10197.1.501）")
    void gmSignedDataUsesSm3WithSm2() throws Exception {
        KeyPair signer = CmsDemo.gmKeyPair();
        X509Certificate cert = CmsDemo.gmSelfSignedCert(signer, new X500Name("CN=GM Signer, O=Study"));
        byte[] signed = CmsDemo.gmSignAttached(signer, cert, DATA);
        org.bouncycastle.cms.CMSSignedData cms = new org.bouncycastle.cms.CMSSignedData(signed);
        org.bouncycastle.cms.SignerInformation si =
                cms.getSignerInfos().getSigners().iterator().next();
        // SM3 摘要 OID 与 SM3withSM2 签名 OID（国密标准 OID）
        assertTrue(si.getDigestAlgorithmID().getAlgorithm().getId().equals("1.2.156.10197.1.401"));
        assertTrue(si.getEncryptionAlgOID().equals("1.2.156.10197.1.501"));
    }

    @Test
    @DisplayName("国密 SignedData：换签名者公钥验证失败（防伪冒）")
    void gmSignedDataWrongKeyFails() {
        KeyPair signer = CmsDemo.gmKeyPair();
        X509Certificate cert = CmsDemo.gmSelfSignedCert(signer, new X500Name("CN=GM Signer, O=Study"));
        byte[] detached = CmsDemo.gmSignDetached(signer, cert, DATA);
        assertFalse(CmsDemo.gmVerifyDetached(CmsDemo.gmKeyPair().getPublic(), DATA, detached));
    }

    @Test
    @DisplayName("国密 EnvelopedData：SM4-CBC 内容加密 + SM2 封装 CEK，收件人开拆还原明文")
    void gmEnvelopeRoundTrip() {
        KeyPair recipient = CmsDemo.gmKeyPair();
        byte[] enveloped = CmsDemo.gmEnvelop(recipient.getPublic(), DATA);
        assertArrayEquals(DATA, CmsDemo.gmOpenEnvelope(recipient.getPrivate(), enveloped));
    }

    @Test
    @DisplayName("国密 EnvelopedData：错误 SM2 私钥开拆被拒绝（SM3 校验失败拿不到 CEK）")
    void gmEnvelopeWrongKeyRejected() {
        KeyPair recipient = CmsDemo.gmKeyPair();
        byte[] enveloped = CmsDemo.gmEnvelop(recipient.getPublic(), DATA);
        KeyPair wrong = CmsDemo.gmKeyPair();
        assertThrows(IllegalStateException.class, () -> CmsDemo.gmOpenEnvelope(wrong.getPrivate(), enveloped));
    }

    @Test
    @DisplayName("PEM 导出往返：BEGIN/END 标记 + base64 解码后还原 DER 字节")
    void toPemRoundTrip() {
        byte[] der = CmsDemo.signAttached(CertificateDemo.generateKeyPair(),
                selfSignedCert(CertificateDemo.generateKeyPair(), "PemSigner"), DATA);
        String pem = CmsDemo.toPem(der, "PKCS7");
        assertTrue(pem.startsWith("-----BEGIN PKCS7-----\n"));
        assertTrue(pem.endsWith("\n-----END PKCS7-----\n"));
        String body = pem.replace("-----BEGIN PKCS7-----\n", "")
                .replace("\n-----END PKCS7-----\n", "");
        assertArrayEquals(der, Base64.getMimeDecoder().decode(body));
    }

    @Test
    @DisplayName("PEM 文件导出：.p7m/.p7s/.p7e 写出后重新解析并验证（openssl 可读格式）")
    void exportedPemFilesRoundTrip(@TempDir Path dir) throws Exception {
        KeyPair signer = CertificateDemo.generateKeyPair();
        X509Certificate signerCert = selfSignedCert(signer, "PemSigner");
        KeyPair recipient = CertificateDemo.generateKeyPair();
        X509Certificate recipientCert = selfSignedCert(recipient, "PemRecipient");

        byte[] attached = CmsDemo.signAttached(signer, signerCert, DATA);
        byte[] detached = CmsDemo.signDetached(signer, signerCert, DATA);
        byte[] enveloped = CmsDemo.envelop(recipientCert, DATA);

        CmsDemo.writePemFile(dir, "attached.p7m", "PKCS7", attached);
        CmsDemo.writePemFile(dir, "detached.p7s", "PKCS7", detached);
        CmsDemo.writePemFile(dir, "enveloped.p7e", "PKCS7", enveloped);
        CmsDemo.writePemFile(dir, "recipient.pem", "CERTIFICATE", recipientCert.getEncoded());
        CmsDemo.writePemFile(dir, "recipient-key.pem", "PRIVATE KEY", recipient.getPrivate().getEncoded());

        // 重新解析 PEM → DER，用我们自己的验证器确认格式有效
        byte[] attachedDer = readPemDer(dir.resolve("attached.p7m"));
        assertTrue(CmsDemo.verifyAttached(signerCert, attachedDer, DATA));

        byte[] detachedDer = readPemDer(dir.resolve("detached.p7s"));
        assertTrue(CmsDemo.verifyDetached(signerCert.getPublicKey(), DATA, detachedDer));

        byte[] envelopedDer = readPemDer(dir.resolve("enveloped.p7e"));
        assertArrayEquals(DATA, CmsDemo.openEnvelope(recipient.getPrivate(), envelopedDer));

        // openssl smime 命令可用的配套文件也导出成功
        assertTrue(Files.exists(dir.resolve("recipient.pem")));
        assertTrue(Files.exists(dir.resolve("recipient-key.pem")));
    }

    private static byte[] readPemDer(Path file) throws Exception {
        // 去掉首行 BEGIN 与末行 END，其余行拼接后 base64 解码（对任意标签名稳健）
        java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
        String body = String.join("", lines.subList(1, lines.size() - 1));
        return Base64.getMimeDecoder().decode(body);
    }
}
