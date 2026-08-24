package com.study.bc.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RsaDemoTest {

    private static RSAKeyParameters pub;
    private static RSAKeyParameters priv;

    @BeforeAll
    static void setUp() {
        AsymmetricCipherKeyPair pair = RsaDemo.generateKeyPair(2048);
        pub = (RSAKeyParameters) pair.getPublic();
        priv = (RSAKeyParameters) pair.getPrivate();
    }

    @Test
    @DisplayName("RSA-2048：模数 2048 位，私钥指数有效")
    void keySize() {
        assertEquals(2048, pub.getModulus().bitLength());
        assertTrue(priv.getExponent().bitLength() > 0);
    }

    @Test
    @DisplayName("RSA PKCS#1 v1.5：公钥加密后私钥解密还原明文")
    void pkcs1RoundTrip() {
        byte[] plain = "RSA PKCS1 测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = RsaDemo.pkcs1Encrypt(pub, plain);
        assertArrayEquals(plain, RsaDemo.pkcs1Decrypt(priv, cipher));
    }

    @Test
    @DisplayName("RSA-OAEP：公钥加密后私钥解密还原明文")
    void oaepRoundTrip() {
        byte[] plain = "RSA OAEP 测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = RsaDemo.oaepEncrypt(pub, plain);
        assertArrayEquals(plain, RsaDemo.oaepDecrypt(priv, cipher));
    }

    @Test
    @DisplayName("RSA-OAEP 随机化：相同明文两次加密密文不同（随机填充）")
    void oaepRandomized() {
        byte[] plain = "随机填充测试".getBytes(StandardCharsets.UTF_8);
        byte[] c1 = RsaDemo.oaepEncrypt(pub, plain);
        byte[] c2 = RsaDemo.oaepEncrypt(pub, plain);
        assertTrue(!java.util.Arrays.equals(c1, c2));
    }

    @Test
    @DisplayName("RSA-OAEP 错误私钥：解密抛异常（密钥不匹配）")
    void wrongKeyFails() {
        AsymmetricCipherKeyPair other = RsaDemo.generateKeyPair(2048);
        byte[] plain = "错钥测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = RsaDemo.oaepEncrypt(pub, plain);
        assertThrows(IllegalStateException.class,
                () -> RsaDemo.oaepDecrypt((RSAKeyParameters) other.getPrivate(), cipher));
    }

    @Test
    @DisplayName("RSA-OAEP 超长明文：超过 190 字节上限被拒绝")
    void tooLongPlaintextRejected() {
        // 2048 位 + OAEP-SHA256：明文上限 190 字节
        byte[] tooLong = new byte[191];
        assertThrows(IllegalStateException.class, () -> RsaDemo.oaepEncrypt(pub, tooLong));
    }
}
