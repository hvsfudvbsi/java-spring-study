package com.study.bc.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.List;

import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.study.bc.asymmetric.Sm2Demo;

class PrivateKeyParserDemoTest {

    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final char[] WRONG_PASSWORD = "wrong-password".toCharArray();

    private static KeyPair rsa;
    private static KeyPair sm2;
    private static ECPublicKeyParameters sm2Pub;

    @BeforeAll
    static void setUp() throws Exception {
        rsa = PrivateKeyParserDemo.generateRsa();
        sm2 = PrivateKeyParserDemo.generateSm2();
        sm2Pub = (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(sm2.getPublic());
    }

    // ---------- RSA ----------

    @Test
    @DisplayName("RSA PKCS#8 PEM（BEGIN PRIVATE KEY）：解析还原字节级一致，未加密")
    void rsaPkcs8PemRoundTrip() {
        String pem = PrivateKeyParserDemo.toPkcs8Pem(rsa.getPrivate());
        assertEquals("PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertFalse(PrivateKeyParserDemo.isEncrypted(pem));
        assertArrayEquals(rsa.getPrivate().getEncoded(), PrivateKeyParserDemo.parsePem(pem).getEncoded());
    }

    @Test
    @DisplayName("RSA PKCS#8 加密 PEM（BEGIN ENCRYPTED PRIVATE KEY）：正确口令解析还原")
    void rsaPkcs8EncryptedPemCorrectPassword() {
        String pem = PrivateKeyParserDemo.toPkcs8EncryptedPem(rsa.getPrivate(), PASSWORD);
        assertEquals("ENCRYPTED PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertTrue(PrivateKeyParserDemo.isEncrypted(pem));
        assertArrayEquals(rsa.getPrivate().getEncoded(), PrivateKeyParserDemo.parsePem(pem, PASSWORD).getEncoded());
    }

    @Test
    @DisplayName("RSA PKCS#8 加密 PEM：错误口令解析被拒绝")
    void rsaPkcs8EncryptedPemWrongPasswordRejected() {
        String pem = PrivateKeyParserDemo.toPkcs8EncryptedPem(rsa.getPrivate(), PASSWORD);
        assertThrows(IllegalStateException.class, () -> PrivateKeyParserDemo.parsePem(pem, WRONG_PASSWORD));
    }

    @Test
    @DisplayName("RSA PKCS#1 传统 PEM（BEGIN RSA PRIVATE KEY）：解析还原")
    void rsaTraditionalPemRoundTrip() {
        String pem = PrivateKeyParserDemo.toTraditionalPem(rsa.getPrivate());
        assertEquals("RSA PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertFalse(PrivateKeyParserDemo.isEncrypted(pem));
        assertArrayEquals(rsa.getPrivate().getEncoded(), PrivateKeyParserDemo.parsePem(pem).getEncoded());
    }

    @Test
    @DisplayName("RSA PKCS#1 传统加密 PEM（Proc-Type 4,ENCRYPTED）：正确口令解析还原")
    void rsaTraditionalEncryptedPemCorrectPassword() {
        String pem = PrivateKeyParserDemo.toTraditionalEncryptedPem(rsa.getPrivate(), PASSWORD);
        assertEquals("RSA PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertTrue(PrivateKeyParserDemo.isEncrypted(pem));
        assertArrayEquals(rsa.getPrivate().getEncoded(), PrivateKeyParserDemo.parsePem(pem, PASSWORD).getEncoded());
    }

    @Test
    @DisplayName("RSA PKCS#1 传统加密 PEM：错误口令解析被拒绝")
    void rsaTraditionalEncryptedPemWrongPasswordRejected() {
        String pem = PrivateKeyParserDemo.toTraditionalEncryptedPem(rsa.getPrivate(), PASSWORD);
        assertThrows(IllegalStateException.class, () -> PrivateKeyParserDemo.parsePem(pem, WRONG_PASSWORD));
    }

    @Test
    @DisplayName("RSA DER（PKCS#8 二进制）：解析还原")
    void rsaDerRoundTrip() {
        byte[] der = PrivateKeyParserDemo.toDer(rsa.getPrivate());
        assertArrayEquals(rsa.getPrivate().getEncoded(), PrivateKeyParserDemo.parseDer(der).getEncoded());
    }

    @Test
    @DisplayName("RSA 口令验证：加密密钥正确口令 true、错误口令 false；未加密恒 true")
    void rsaCheckPassword() {
        String plain = PrivateKeyParserDemo.toPkcs8Pem(rsa.getPrivate());
        assertTrue(PrivateKeyParserDemo.checkPassword(plain, WRONG_PASSWORD), "未加密私钥无口令可验");

        String encPkcs8 = PrivateKeyParserDemo.toPkcs8EncryptedPem(rsa.getPrivate(), PASSWORD);
        assertTrue(PrivateKeyParserDemo.checkPassword(encPkcs8, PASSWORD));
        assertFalse(PrivateKeyParserDemo.checkPassword(encPkcs8, WRONG_PASSWORD));

        String encTraditional = PrivateKeyParserDemo.toTraditionalEncryptedPem(rsa.getPrivate(), PASSWORD);
        assertTrue(PrivateKeyParserDemo.checkPassword(encTraditional, PASSWORD));
        assertFalse(PrivateKeyParserDemo.checkPassword(encTraditional, WRONG_PASSWORD));
    }

    @Test
    @DisplayName("RSA 各格式解析出的私钥均可直接用于签名，原公钥验签通过")
    void rsaParsedKeyUsableForSigning() throws Exception {
        byte[] data = "RSA 私钥解析可用性".getBytes(StandardCharsets.UTF_8);
        List<String> pems = List.of(
                PrivateKeyParserDemo.toPkcs8Pem(rsa.getPrivate()),
                PrivateKeyParserDemo.toPkcs8EncryptedPem(rsa.getPrivate(), PASSWORD),
                PrivateKeyParserDemo.toTraditionalPem(rsa.getPrivate()),
                PrivateKeyParserDemo.toTraditionalEncryptedPem(rsa.getPrivate(), PASSWORD));
        for (String pem : pems) {
            PrivateKey parsed = PrivateKeyParserDemo.parsePem(pem, PASSWORD);
            assertArrayEquals(rsa.getPrivate().getEncoded(), parsed.getEncoded());

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(parsed);
            signer.update(data);
            byte[] sig = signer.sign();

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(rsa.getPublic());
            verifier.update(data);
            assertTrue(verifier.verify(sig), "解析私钥签名后验签失败: " + PrivateKeyParserDemo.pemType(pem));
        }
    }

    // ---------- SM2 ----------

    @Test
    @DisplayName("SM2 PKCS#8 PEM（BEGIN PRIVATE KEY）：解析还原字节级一致")
    void sm2Pkcs8PemRoundTrip() {
        String pem = PrivateKeyParserDemo.toPkcs8Pem(sm2.getPrivate());
        assertEquals("PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertArrayEquals(sm2.getPrivate().getEncoded(), PrivateKeyParserDemo.parsePem(pem).getEncoded());
    }

    @Test
    @DisplayName("SM2 PKCS#8 加密 PEM：正确口令解析还原")
    void sm2Pkcs8EncryptedPemCorrectPassword() {
        String pem = PrivateKeyParserDemo.toPkcs8EncryptedPem(sm2.getPrivate(), PASSWORD);
        assertEquals("ENCRYPTED PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertTrue(PrivateKeyParserDemo.isEncrypted(pem));
        assertArrayEquals(sm2.getPrivate().getEncoded(), PrivateKeyParserDemo.parsePem(pem, PASSWORD).getEncoded());
    }

    @Test
    @DisplayName("SM2 PKCS#8 加密 PEM：错误口令解析被拒绝")
    void sm2Pkcs8EncryptedPemWrongPasswordRejected() {
        String pem = PrivateKeyParserDemo.toPkcs8EncryptedPem(sm2.getPrivate(), PASSWORD);
        assertThrows(IllegalStateException.class, () -> PrivateKeyParserDemo.parsePem(pem, WRONG_PASSWORD));
    }

    @Test
    @DisplayName("SM2 SEC1 传统 EC PEM（BEGIN EC PRIVATE KEY）：解析还原，曲线保留")
    void sm2TraditionalEcPemRoundTrip() throws Exception {
        String pem = PrivateKeyParserDemo.toTraditionalPem(sm2.getPrivate());
        assertEquals("EC PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertFalse(PrivateKeyParserDemo.isEncrypted(pem));
        // SEC1 输出用命名曲线 OID（sm2p256v1 → 1.2.156.10197.1.301），与原始 PKCS#8 的
        // 显式参数编码形态不同，故按密钥材料（私钥标量 d + 曲线）断言还原而非 PKCS#8 字节；
        // 同时验证 SEC1 编码确定性：解析后再编码与原始 PEM 逐字节一致
        PrivateKey parsed = PrivateKeyParserDemo.parsePem(pem);
        assertSameSm2Key(sm2.getPrivate(), parsed);
        assertEquals(pem, PrivateKeyParserDemo.toTraditionalPem(parsed));
    }

    @Test
    @DisplayName("SM2 SEC1 传统加密 EC PEM（Proc-Type 4,ENCRYPTED）：正确口令解析还原")
    void sm2TraditionalEcEncryptedPemCorrectPassword() throws Exception {
        String pem = PrivateKeyParserDemo.toTraditionalEncryptedPem(sm2.getPrivate(), PASSWORD);
        assertEquals("EC PRIVATE KEY", PrivateKeyParserDemo.pemType(pem));
        assertTrue(PrivateKeyParserDemo.isEncrypted(pem));
        PrivateKey parsed = PrivateKeyParserDemo.parsePem(pem, PASSWORD);
        assertSameSm2Key(sm2.getPrivate(), parsed);
        // 加密 PEM 解密后重编码为未加密 SEC1，仍与标准 OID 形态逐字节一致
        assertEquals(PrivateKeyParserDemo.toTraditionalPem(sm2.getPrivate()), PrivateKeyParserDemo.toTraditionalPem(parsed));
    }

    @Test
    @DisplayName("SM2 SEC1 传统加密 EC PEM：错误口令解析被拒绝")
    void sm2TraditionalEcEncryptedPemWrongPasswordRejected() {
        String pem = PrivateKeyParserDemo.toTraditionalEncryptedPem(sm2.getPrivate(), PASSWORD);
        assertThrows(IllegalStateException.class, () -> PrivateKeyParserDemo.parsePem(pem, WRONG_PASSWORD));
    }

    @Test
    @DisplayName("SM2 DER（PKCS#8 二进制）：解析还原")
    void sm2DerRoundTrip() {
        byte[] der = PrivateKeyParserDemo.toDer(sm2.getPrivate());
        assertArrayEquals(sm2.getPrivate().getEncoded(), PrivateKeyParserDemo.parseDer(der).getEncoded());
    }

    @Test
    @DisplayName("SM2 口令验证：加密密钥正确口令 true、错误口令 false；未加密恒 true")
    void sm2CheckPassword() {
        String plain = PrivateKeyParserDemo.toPkcs8Pem(sm2.getPrivate());
        assertTrue(PrivateKeyParserDemo.checkPassword(plain, WRONG_PASSWORD), "未加密私钥无口令可验");

        String encPkcs8 = PrivateKeyParserDemo.toPkcs8EncryptedPem(sm2.getPrivate(), PASSWORD);
        assertTrue(PrivateKeyParserDemo.checkPassword(encPkcs8, PASSWORD));
        assertFalse(PrivateKeyParserDemo.checkPassword(encPkcs8, WRONG_PASSWORD));

        String encTraditional = PrivateKeyParserDemo.toTraditionalEncryptedPem(sm2.getPrivate(), PASSWORD);
        assertTrue(PrivateKeyParserDemo.checkPassword(encTraditional, PASSWORD));
        assertFalse(PrivateKeyParserDemo.checkPassword(encTraditional, WRONG_PASSWORD));
    }

    @Test
    @DisplayName("SM2 各格式解析出的私钥均可用于 SM3withSM2 签名验签")
    void sm2ParsedKeyUsableForSigning() throws Exception {
        byte[] data = "国密 SM2 私钥解析可用性".getBytes(StandardCharsets.UTF_8);
        List<String> pems = List.of(
                PrivateKeyParserDemo.toPkcs8Pem(sm2.getPrivate()),
                PrivateKeyParserDemo.toPkcs8EncryptedPem(sm2.getPrivate(), PASSWORD),
                PrivateKeyParserDemo.toTraditionalPem(sm2.getPrivate()),
                PrivateKeyParserDemo.toTraditionalEncryptedPem(sm2.getPrivate(), PASSWORD));
        for (String pem : pems) {
            PrivateKey parsed = PrivateKeyParserDemo.parsePem(pem, PASSWORD);
            ECPrivateKeyParameters priv = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(parsed);
            byte[] sig = Sm2Demo.sign(priv, data);
            assertTrue(Sm2Demo.verify(sm2Pub, data, sig),
                    "解析私钥签名后验签失败: " + PrivateKeyParserDemo.pemType(pem));
        }
    }

    /** 断言两把 SM2/EC 私钥的密钥材料一致（私钥标量 d 相同、曲线相同）。 */
    private static void assertSameSm2Key(PrivateKey expected, PrivateKey actual) throws Exception {
        ECPrivateKeyParameters exp = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(expected);
        ECPrivateKeyParameters act = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(actual);
        assertEquals(exp.getD(), act.getD(), "SM2 私钥标量 d 不一致");
        assertEquals(exp.getParameters().getCurve(), act.getParameters().getCurve(), "SM2 曲线不一致");
    }

    // ---------- 文件场景 ----------

    @Test
    @DisplayName("私钥文件落盘后重读解析：PEM/DER 文件均可还原（真实文件场景）")
    void keyFileRoundTrip(@TempDir Path dir) throws Exception {
        // RSA PKCS#8 PEM 文件
        Path rsaPemFile = dir.resolve("rsa-private.pem");
        Files.writeString(rsaPemFile, PrivateKeyParserDemo.toPkcs8Pem(rsa.getPrivate()));
        assertArrayEquals(rsa.getPrivate().getEncoded(),
                PrivateKeyParserDemo.parsePem(Files.readString(rsaPemFile)).getEncoded());

        // SM2 加密 PKCS#8 PEM 文件（需口令）
        Path sm2PemFile = dir.resolve("sm2-encrypted.pem");
        Files.writeString(sm2PemFile, PrivateKeyParserDemo.toPkcs8EncryptedPem(sm2.getPrivate(), PASSWORD));
        assertArrayEquals(sm2.getPrivate().getEncoded(),
                PrivateKeyParserDemo.parsePem(Files.readString(sm2PemFile), PASSWORD).getEncoded());
        assertFalse(PrivateKeyParserDemo.checkPassword(Files.readString(sm2PemFile), WRONG_PASSWORD));

        // RSA DER 二进制文件
        Path derFile = dir.resolve("rsa-private.der");
        Files.write(derFile, PrivateKeyParserDemo.toDer(rsa.getPrivate()));
        assertArrayEquals(rsa.getPrivate().getEncoded(),
                PrivateKeyParserDemo.parseDer(Files.readAllBytes(derFile)).getEncoded());
    }
}