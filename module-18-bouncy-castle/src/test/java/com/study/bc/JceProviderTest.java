package com.study.bc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JCE 标准接口获取算法实例的测试用例。
 *
 * <p>验证通过 {@code KeyPairGenerator.getInstance()} / {@code Signature.getInstance()} /
 * {@code Cipher.getInstance()} / {@code KeyAgreement.getInstance()} /
 * {@code KeyFactory.getInstance()} / {@code MessageDigest.getInstance()} /
 * {@code Mac.getInstance()} / {@code KeyGenerator.getInstance()} /
 * {@code CertificateFactory.getInstance()} / {@code CertPathValidator.getInstance()} /
 * {@code KeyStore.getInstance()} 等
 * JCE 标准接口获取的算法实例均可正常使用（生成密钥、签名/验签、加密/解密等往返闭环）。
 *
 * <p>覆盖两类 Provider：
 * <ul>
 *   <li>JDK 内置（无参或 "SunEC"/"SunRsaSign"）：RSA、EC、DH、DSA、Ed25519、AES、HmacSHA256；</li>
 *   <li>BC Provider（指定 "BC"）：SM2（EC 曲线 sm2p256v1）、SM3、SM4、SM3withSM2。</li>
 *   <li>证书/密钥库接口：CertificateFactory X.509、CertPathValidator PKIX、KeyStore PKCS12。</li>
 * </ul>
 */
class JceProviderTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] DATA = "JCE 标准接口获取算法测试".getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void registerBc() {
        BcSupport.register();
    }

    // ==================== KeyPairGenerator 密钥对生成 ====================

    @Test
    @DisplayName("KeyPairGenerator: RSA-2048 密钥对，往返编码一致")
    void rsaKeyPairGenerator() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        assertNotNull(pair.getPublic());
        assertNotNull(pair.getPrivate());
        // X.509 / PKCS#8 编码往返一致
        KeyFactory kf = KeyFactory.getInstance("RSA");
        assertArrayEquals(pair.getPublic().getEncoded(),
                kf.generatePublic(new X509EncodedKeySpec(pair.getPublic().getEncoded())).getEncoded());
        assertArrayEquals(pair.getPrivate().getEncoded(),
                kf.generatePrivate(new PKCS8EncodedKeySpec(pair.getPrivate().getEncoded())).getEncoded());
    }

    @Test
    @DisplayName("KeyPairGenerator: EC-P256 密钥对（BC provider），生成成功")
    void ecKeyPairGenerator() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        assertEquals("EC", pair.getPublic().getAlgorithm());
        assertEquals("EC", pair.getPrivate().getAlgorithm());
    }

    @Test
    @DisplayName("KeyPairGenerator: DH-2048 密钥对，生成成功")
    void dhKeyPairGenerator() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(2048, RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        assertEquals("DH", pair.getPublic().getAlgorithm());
    }

    @Test
    @DisplayName("KeyPairGenerator: Ed25519 密钥对，生成成功")
    void ed25519KeyPairGenerator() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = kpg.generateKeyPair();
        assertEquals("EdDSA", pair.getPublic().getAlgorithm()); // JDK 返回 EdDSA 族名
    }

    @Test
    @DisplayName("KeyPairGenerator: DSA-2048 密钥对，生成成功")
    void dsaKeyPairGenerator() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
        kpg.initialize(2048, RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        assertEquals("DSA", pair.getPublic().getAlgorithm());
    }

    @Test
    @DisplayName("KeyPairGenerator: SM2 密钥对（EC 曲线 sm2p256v1, BC provider）")
    void sm2KeyPairGenerator() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("sm2p256v1"), RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        assertEquals("EC", pair.getPublic().getAlgorithm());
    }

    // ==================== Signature 签名与验签 ====================

    @Test
    @DisplayName("Signature: SHA256withRSA 签名验签往返")
    void sha256WithRsaSignature() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update(DATA);
        assertTrue(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: SHA256withRSA 篡改数据验签失败")
    void sha256WithRsaTampered() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update("篡改数据".getBytes(StandardCharsets.UTF_8));
        assertFalse(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: SHA256withECDSA（BC provider）签名验签往返")
    void sha256WithEcdsaSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update(DATA);
        assertTrue(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: Ed25519 签名验签往返")
    void ed25519Signature() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update(DATA);
        assertTrue(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: SHA256withDSA 签名验签往返")
    void sha256WithDsaSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
        kpg.initialize(2048, RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        Signature sig = Signature.getInstance("SHA256withDSA");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update(DATA);
        assertTrue(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: SM3withSM2（BC provider）签名验签往返")
    void sm3WithSm2Signature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("sm2p256v1"), RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        Signature sig = Signature.getInstance("SM3withSM2", "BC");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update(DATA);
        assertTrue(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: SM3withSM2 篡改数据验签失败")
    void sm3WithSm2Tampered() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("sm2p256v1"), RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        Signature sig = Signature.getInstance("SM3withSM2", "BC");
        sig.initSign(pair.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(pair.getPublic());
        sig.update("篡改数据".getBytes(StandardCharsets.UTF_8));
        assertFalse(sig.verify(signature));
    }

    @Test
    @DisplayName("Signature: SM3withSM2 换公钥验签失败（防伪冒）")
    void sm3WithSm2WrongKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("sm2p256v1"), RANDOM);
        KeyPair signer = kpg.generateKeyPair();
        KeyPair other = kpg.generateKeyPair();
        Signature sig = Signature.getInstance("SM3withSM2", "BC");
        sig.initSign(signer.getPrivate(), RANDOM);
        sig.update(DATA);
        byte[] signature = sig.sign();
        sig.initVerify(other.getPublic());
        sig.update(DATA);
        assertFalse(sig.verify(signature));
    }

    // ==================== Cipher 加密与解密 ====================

    @Test
    @DisplayName("Cipher: AES-CBC 加密解密往返（JCE 接口）")
    void aesCbcCipher() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        SecretKey sk = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, sk, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(DATA);
        cipher.init(Cipher.DECRYPT_MODE, sk, new IvParameterSpec(iv));
        assertArrayEquals(DATA, cipher.doFinal(encrypted));
    }

    @Test
    @DisplayName("Cipher: AES-CBC 错误密钥解密抛异常")
    void aesCbcWrongKey() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        byte[] wrongKey = new byte[16];
        RANDOM.nextBytes(wrongKey);
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        SecretKey sk = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, sk, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(DATA);
        SecretKey wrongSk = new SecretKeySpec(wrongKey, "AES");
        cipher.init(Cipher.DECRYPT_MODE, wrongSk, new IvParameterSpec(iv));
        assertThrows(Exception.class, () -> cipher.doFinal(encrypted));
    }

    @Test
    @DisplayName("Cipher: AES-GCM 加密解密往返（JCE 接口，认证加密）")
    void aesGcmCipher() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        SecretKey sk = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, sk, new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(DATA);
        cipher.init(Cipher.DECRYPT_MODE, sk, new javax.crypto.spec.GCMParameterSpec(128, iv));
        assertArrayEquals(DATA, cipher.doFinal(encrypted));
    }

    @Test
    @DisplayName("Cipher: SM4-CBC 加密解密往返（BC provider, JCE 接口）")
    void sm4CbcCipher() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        SecretKey sk = new SecretKeySpec(key, "SM4");
        Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, sk, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(DATA);
        cipher.init(Cipher.DECRYPT_MODE, sk, new IvParameterSpec(iv));
        assertArrayEquals(DATA, cipher.doFinal(encrypted));
    }

    @Test
    @DisplayName("Cipher: SM4-CBC 错误密钥解密抛异常")
    void sm4CbcWrongKey() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        byte[] wrongKey = new byte[16];
        RANDOM.nextBytes(wrongKey);
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        SecretKey sk = new SecretKeySpec(key, "SM4");
        Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, sk, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(DATA);
        SecretKey wrongSk = new SecretKeySpec(wrongKey, "SM4");
        cipher.init(Cipher.DECRYPT_MODE, wrongSk, new IvParameterSpec(iv));
        assertThrows(Exception.class, () -> cipher.doFinal(encrypted));
    }

    @Test
    @DisplayName("Cipher: SM4-ECB 加密解密往返（BC provider, JCE 接口）")
    void sm4EcbCipher() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        // 填充到 16 字节倍数
        byte[] padded = new byte[16];
        System.arraycopy(DATA, 0, padded, 0, Math.min(DATA.length, 16));
        SecretKey sk = new SecretKeySpec(key, "SM4");
        Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS7Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, sk);
        byte[] encrypted = cipher.doFinal(padded);
        cipher.init(Cipher.DECRYPT_MODE, sk);
        assertArrayEquals(padded, cipher.doFinal(encrypted));
    }

    // ==================== KeyAgreement 密钥协商 ====================

    @Test
    @DisplayName("KeyAgreement: ECDH（BC provider）双方共享秘密一致")
    void ecdhKeyAgreement() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair alice = kpg.generateKeyPair();
        KeyPair bob = kpg.generateKeyPair();
        KeyAgreement aliceAgree = KeyAgreement.getInstance("ECDH", "BC");
        aliceAgree.init(alice.getPrivate());
        aliceAgree.doPhase(bob.getPublic(), true);
        byte[] aliceSecret = aliceAgree.generateSecret();
        KeyAgreement bobAgree = KeyAgreement.getInstance("ECDH", "BC");
        bobAgree.init(bob.getPrivate());
        bobAgree.doPhase(alice.getPublic(), true);
        byte[] bobSecret = bobAgree.generateSecret();
        assertArrayEquals(aliceSecret, bobSecret);
    }

    @Test
    @DisplayName("KeyAgreement: DH 双方共享秘密一致")
    void dhKeyAgreement() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(2048, RANDOM);
        KeyPair alice = kpg.generateKeyPair();
        KeyPair bob = kpg.generateKeyPair();
        KeyAgreement aliceAgree = KeyAgreement.getInstance("DH");
        aliceAgree.init(alice.getPrivate());
        aliceAgree.doPhase(bob.getPublic(), true);
        byte[] aliceSecret = aliceAgree.generateSecret();
        KeyAgreement bobAgree = KeyAgreement.getInstance("DH");
        bobAgree.init(bob.getPrivate());
        bobAgree.doPhase(alice.getPublic(), true);
        byte[] bobSecret = bobAgree.generateSecret();
        assertArrayEquals(aliceSecret, bobSecret);
    }

    // ==================== KeyFactory 密钥编解码 ====================

    @Test
    @DisplayName("KeyFactory: RSA 公钥私钥 X.509/PKCS#8 编解码往返一致")
    void rsaKeyFactory() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        assertArrayEquals(pair.getPublic().getEncoded(),
                kf.generatePublic(new X509EncodedKeySpec(pair.getPublic().getEncoded())).getEncoded());
        assertArrayEquals(pair.getPrivate().getEncoded(),
                kf.generatePrivate(new PKCS8EncodedKeySpec(pair.getPrivate().getEncoded())).getEncoded());
    }

    @Test
    @DisplayName("KeyFactory: EC（BC provider）公钥私钥编解码往返一致")
    void ecKeyFactory() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair pair = kpg.generateKeyPair();
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        assertArrayEquals(pair.getPublic().getEncoded(),
                kf.generatePublic(new X509EncodedKeySpec(pair.getPublic().getEncoded())).getEncoded());
        assertArrayEquals(pair.getPrivate().getEncoded(),
                kf.generatePrivate(new PKCS8EncodedKeySpec(pair.getPrivate().getEncoded())).getEncoded());
    }

    @Test
    @DisplayName("KeyFactory: Ed25519 公钥私钥编解码往返一致")
    void ed25519KeyFactory() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        assertArrayEquals(pair.getPublic().getEncoded(),
                kf.generatePublic(new X509EncodedKeySpec(pair.getPublic().getEncoded())).getEncoded());
        // Ed25519 私钥使用 PKCS8EncodedKeySpec 解码
        assertArrayEquals(pair.getPrivate().getEncoded(),
                kf.generatePrivate(new PKCS8EncodedKeySpec(pair.getPrivate().getEncoded())).getEncoded());
    }

    // ==================== MessageDigest 消息摘要 ====================

    @Test
    @DisplayName("MessageDigest: SHA-256 摘要长度 32 字节，确定性输出一致")
    void sha256Digest() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d1 = md.digest(DATA);
        byte[] d2 = md.digest(DATA);
        assertEquals(32, d1.length);
        assertArrayEquals(d1, d2);
    }

    @Test
    @DisplayName("MessageDigest: SM3（BC provider）摘要长度 32 字节，确定性输出一致")
    void sm3Digest() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SM3", "BC");
        byte[] d1 = md.digest(DATA);
        byte[] d2 = md.digest(DATA);
        assertEquals(32, d1.length);
        assertArrayEquals(d1, d2);
    }

    @Test
    @DisplayName("MessageDigest: SHA-256 不同输入摘要不同（抗碰撞）")
    void sha256DifferentInputDifferentDigest() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d1 = md.digest(DATA);
        byte[] d2 = md.digest("其他数据".getBytes(StandardCharsets.UTF_8));
        assertFalse(java.util.Arrays.equals(d1, d2));
    }

    // ==================== Mac 消息认证码 ====================

    @Test
    @DisplayName("Mac: HmacSHA256 标签长度 32 字节，确定性输出一致")
    void hmacSha256() throws Exception {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        SecretKey sk = new SecretKeySpec(key, "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(sk);
        byte[] t1 = mac.doFinal(DATA);
        byte[] t2 = mac.doFinal(DATA);
        assertEquals(32, t1.length);
        assertArrayEquals(t1, t2);
    }

    @Test
    @DisplayName("Mac: HmacSHA256 不同密钥不同标签")
    void hmacSha256WrongKey() throws Exception {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        byte[] wrongKey = new byte[32];
        RANDOM.nextBytes(wrongKey);
        SecretKey sk = new SecretKeySpec(key, "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(sk);
        byte[] t1 = mac.doFinal(DATA);
        SecretKey wrongSk = new SecretKeySpec(wrongKey, "HmacSHA256");
        mac.init(wrongSk);
        byte[] t2 = mac.doFinal(DATA);
        assertFalse(java.util.Arrays.equals(t1, t2));
    }

    @Test
    @DisplayName("Mac: HmacSHA512 标签长度 64 字节")
    void hmacSha512() throws Exception {
        byte[] key = new byte[64];
        RANDOM.nextBytes(key);
        SecretKey sk = new SecretKeySpec(key, "HmacSHA512");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(sk);
        assertEquals(64, mac.doFinal(DATA).length);
    }

    // ==================== BC 独有 JCE 算法（仅 BC provider 提供） ====================

    @Test
    @DisplayName("Mac: HmacSM3（BC 独有）标签长度 32 字节，确定性输出一致")
    void hmacSm3() throws Exception {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        SecretKey sk = new SecretKeySpec(key, "HmacSM3");
        Mac mac = Mac.getInstance("HmacSM3", "BC");
        mac.init(sk);
        byte[] t1 = mac.doFinal(DATA);
        byte[] t2 = mac.doFinal(DATA);
        assertEquals(32, t1.length);
        assertArrayEquals(t1, t2);
    }

    @Test
    @DisplayName("Mac: HmacSM3 不同密钥不同标签（抗碰撞）")
    void hmacSm3WrongKey() throws Exception {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        byte[] wrongKey = new byte[32];
        RANDOM.nextBytes(wrongKey);
        SecretKey sk = new SecretKeySpec(key, "HmacSM3");
        Mac mac = Mac.getInstance("HmacSM3", "BC");
        mac.init(sk);
        byte[] t1 = mac.doFinal(DATA);
        SecretKey wrongSk = new SecretKeySpec(wrongKey, "HmacSM3");
        mac.init(wrongSk);
        byte[] t2 = mac.doFinal(DATA);
        assertFalse(java.util.Arrays.equals(t1, t2));
    }

    @Test
    @DisplayName("MessageDigest: RIPEMD160（BC 独有）摘要 20 字节，确定性输出一致")
    void ripemd160Digest() throws Exception {
        MessageDigest md = MessageDigest.getInstance("RIPEMD160", "BC");
        byte[] d1 = md.digest(DATA);
        byte[] d2 = md.digest(DATA);
        assertEquals(20, d1.length);
        assertArrayEquals(d1, d2);
    }

    @Test
    @DisplayName("MessageDigest: WHIRLPOOL（BC 独有）摘要 64 字节，确定性输出一致")
    void whirlpoolDigest() throws Exception {
        MessageDigest md = MessageDigest.getInstance("WHIRLPOOL", "BC");
        byte[] d1 = md.digest(DATA);
        byte[] d2 = md.digest(DATA);
        assertEquals(64, d1.length);
        assertArrayEquals(d1, d2);
    }

    @Test
    @DisplayName("KeyGenerator: HmacSM3（BC 独有）生成密钥后 Mac 协同使用往返一致")
    void hmacSm3KeyGeneratorThenMac() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("HmacSM3", "BC");
        kg.init(256, RANDOM);
        SecretKey key = kg.generateKey();
        Mac mac = Mac.getInstance("HmacSM3", "BC");
        mac.init(key);
        byte[] t1 = mac.doFinal(DATA);
        mac.init(key);
        byte[] t2 = mac.doFinal(DATA);
        assertArrayEquals(t1, t2);
    }

    @Test
    @DisplayName("AlgorithmParameters: SM4（BC 独有）IV 编码后再解码得到相同 IV")
    void sm4AlgorithmParameters() throws Exception {
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        AlgorithmParameters ap = AlgorithmParameters.getInstance("SM4", "BC");
        ap.init(new IvParameterSpec(iv));
        byte[] encoded = ap.getEncoded();
        AlgorithmParameters ap2 = AlgorithmParameters.getInstance("SM4", "BC");
        ap2.init(encoded);
        assertArrayEquals(iv, ap2.getParameterSpec(IvParameterSpec.class).getIV());
    }

    @Test
    @DisplayName("AlgorithmParameters: SM4 构造的 IV 参数传给 Cipher CBC，encrypt/decrypt 往返一致")
    void sm4AlgorithmParametersWithCipher() throws Exception {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        SecretKey sk = new SecretKeySpec(key, "SM4");
        // 用 AlgorithmParameters 封装 IV 再传给 Cipher（与直接 new IvParameterSpec 等价）
        AlgorithmParameters ap = AlgorithmParameters.getInstance("SM4", "BC");
        ap.init(new IvParameterSpec(iv));
        Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, sk, ap);
        byte[] encrypted = cipher.doFinal(DATA);
        cipher.init(Cipher.DECRYPT_MODE, sk, ap);
        assertArrayEquals(DATA, cipher.doFinal(encrypted));
    }

    // ==================== KeyGenerator 对称密钥生成 ====================

    @Test
    @DisplayName("KeyGenerator: AES-128 密钥生成，长度 16 字节")
    void aesKeyGenerator() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128, RANDOM);
        SecretKey key = kg.generateKey();
        assertEquals("AES", key.getAlgorithm());
        assertEquals(16, key.getEncoded().length);
    }

    @Test
    @DisplayName("KeyGenerator: AES-256 密钥生成，长度 32 字节")
    void aes256KeyGenerator() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, RANDOM);
        SecretKey key = kg.generateKey();
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    @DisplayName("KeyGenerator: SM4（BC provider）密钥生成，长度 16 字节")
    void sm4KeyGenerator() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("SM4", "BC");
        kg.init(128, RANDOM);
        SecretKey key = kg.generateKey();
        assertEquals("SM4", key.getAlgorithm());
        assertEquals(16, key.getEncoded().length);
    }

    @Test
    @DisplayName("KeyGenerator: HmacSHA256 密钥生成，与 Mac 协同使用往返一致")
    void hmacSha256KeyGeneratorThenMac() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("HmacSHA256");
        kg.init(256, RANDOM);
        SecretKey key = kg.generateKey();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] t1 = mac.doFinal(DATA);
        mac.init(key);
        byte[] t2 = mac.doFinal(DATA);
        assertArrayEquals(t1, t2);
    }

    // ==================== CertificateFactory 证书工厂 ====================

    /** 签发一张自签名 RSA 证书供 CertificateFactory/CertPathValidator 测试使用。 */
    private static X509Certificate selfSignedCert() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, RANDOM);
        KeyPair ca = kpg.generateKeyPair();
        X500Name name = new X500Name("CN=JCE Test CA");
        long now = System.currentTimeMillis();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, new BigInteger(64, RANDOM),
                new Date(now - 86_400_000L), new Date(now + 365L * 86_400_000L),
                name, ca.getPublic());
        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(ca.getPrivate())));
    }

    @Test
    @DisplayName("CertificateFactory: X.509 证书 DER 编解码往返一致")
    void certificateFactoryDerRoundTrip() throws Exception {
        X509Certificate cert = selfSignedCert();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate restored = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(cert.getEncoded()));
        assertArrayEquals(cert.getEncoded(), restored.getEncoded());
        assertEquals(cert.getSubjectX500Principal(), restored.getSubjectX500Principal());
        assertEquals(cert.getSerialNumber(), restored.getSerialNumber());
    }

    @Test
    @DisplayName("CertificateFactory: generateCertPath 生成 CertPath，证书列表往返一致")
    void certificateFactoryCertPath() throws Exception {
        X509Certificate cert = selfSignedCert();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath certPath = cf.generateCertPath(List.of(cert));
        assertEquals("X.509", certPath.getType());
        assertEquals(1, certPath.getCertificates().size());
        X509Certificate fromPath = (X509Certificate) certPath.getCertificates().get(0);
        assertArrayEquals(cert.getEncoded(), fromPath.getEncoded());
    }

    @Test
    @DisplayName("CertificateFactory: 非 X.509 编码字节流抛 CertificateException")
    void certificateFactoryInvalidDer() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        assertThrows(java.security.cert.CertificateException.class,
                () -> cf.generateCertificate(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
    }

    // ==================== CertPathValidator 证书路径验证 ====================

    @Test
    @DisplayName("CertPathValidator: PKIX 单级自签名证书信任链验证通过")
    void certPathValidatorSelfSignedPasses() throws Exception {
        X509Certificate ca = selfSignedCert();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath path = cf.generateCertPath(List.of(ca));
        PKIXParameters params = new PKIXParameters(Set.of(new TrustAnchor(ca, null)));
        params.setRevocationEnabled(false);
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        // 不抛异常即通过
        validator.validate(path, params);
    }

    @Test
    @DisplayName("CertPathValidator: PKIX 无关 TrustAnchor 验证失败抛 CertPathValidatorException")
    void certPathValidatorWrongAnchorFails() throws Exception {
        X509Certificate leaf = selfSignedCert();
        X509Certificate unrelatedAnchor = selfSignedCert();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath path = cf.generateCertPath(List.of(leaf));
        PKIXParameters params = new PKIXParameters(Set.of(new TrustAnchor(unrelatedAnchor, null)));
        params.setRevocationEnabled(false);
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        assertThrows(java.security.cert.CertPathValidatorException.class,
                () -> validator.validate(path, params));
    }

    // ==================== KeyStore 密钥库 ====================

    @Test
    @DisplayName("KeyStore: PKCS12 存储私钥+证书链，读回后字节级一致")
    void keyStorePkcs12RoundTrip() throws Exception {
        X509Certificate ca = selfSignedCert();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, RANDOM);
        KeyPair server = kpg.generateKeyPair();
        String alias = "test-server";
        char[] password = "changeit".toCharArray();
        // 写
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, server.getPrivate(), password,
                new java.security.cert.Certificate[] {ca});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, password);
        byte[] p12 = out.toByteArray();
        // 读
        KeyStore restored = KeyStore.getInstance("PKCS12");
        restored.load(new ByteArrayInputStream(p12), password);
        assertArrayEquals(server.getPrivate().getEncoded(),
                restored.getKey(alias, password).getEncoded());
        assertArrayEquals(ca.getEncoded(), restored.getCertificate(alias).getEncoded());
        assertArrayEquals(ca.getEncoded(), restored.getCertificateChain(alias)[0].getEncoded());
    }

    @Test
    @DisplayName("KeyStore: PKCS12 错误口令加载抛 IOException")
    void keyStorePkcs12WrongPassword() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, RANDOM);
        KeyPair server = kpg.generateKeyPair();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("test", server.getPrivate(), "correct".toCharArray(),
                new java.security.cert.Certificate[] {selfSignedCert()});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, "correct".toCharArray());
        byte[] p12 = out.toByteArray();
        KeyStore bad = KeyStore.getInstance("PKCS12");
        assertThrows(java.io.IOException.class,
                () -> bad.load(new ByteArrayInputStream(p12), "wrong".toCharArray()));
    }

    // ==================== 未知算法 & Provider 边界 ====================

    @Test
    @DisplayName("未知算法：MessageDigest 抛 NoSuchAlgorithmException")
    void unknownMessageDigest() {
        assertThrows(java.security.NoSuchAlgorithmException.class,
                () -> MessageDigest.getInstance("UNKNOWN-ALGO-999"));
    }

    @Test
    @DisplayName("未知算法：Cipher 抛 NoSuchAlgorithmException")
    void unknownCipher() {
        assertThrows(java.security.NoSuchAlgorithmException.class,
                () -> Cipher.getInstance("UNKNOWN/CIPHER/MODE"));
    }

    @Test
    @DisplayName("未知算法：KeyPairGenerator 抛 NoSuchAlgorithmException")
    void unknownKeyPairGenerator() {
        assertThrows(java.security.NoSuchAlgorithmException.class,
                () -> KeyPairGenerator.getInstance("UNKNOWN-KPG-999"));
    }
}