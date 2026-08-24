package com.study.bc.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.jupiter.api.BeforeAll;
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
    void keySize() {
        assertEquals(2048, pub.getModulus().bitLength());
        assertTrue(priv.getExponent().bitLength() > 0);
    }

    @Test
    void pkcs1RoundTrip() {
        byte[] plain = "RSA PKCS1 测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = RsaDemo.pkcs1Encrypt(pub, plain);
        assertArrayEquals(plain, RsaDemo.pkcs1Decrypt(priv, cipher));
    }

    @Test
    void oaepRoundTrip() {
        byte[] plain = "RSA OAEP 测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = RsaDemo.oaepEncrypt(pub, plain);
        assertArrayEquals(plain, RsaDemo.oaepDecrypt(priv, cipher));
    }

    @Test
    void oaepRandomized() {
        byte[] plain = "随机填充测试".getBytes(StandardCharsets.UTF_8);
        byte[] c1 = RsaDemo.oaepEncrypt(pub, plain);
        byte[] c2 = RsaDemo.oaepEncrypt(pub, plain);
        assertTrue(!java.util.Arrays.equals(c1, c2));
    }

    @Test
    void wrongKeyFails() {
        AsymmetricCipherKeyPair other = RsaDemo.generateKeyPair(2048);
        byte[] plain = "错钥测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = RsaDemo.oaepEncrypt(pub, plain);
        assertThrows(IllegalStateException.class,
                () -> RsaDemo.oaepDecrypt((RSAKeyParameters) other.getPrivate(), cipher));
    }

    @Test
    void tooLongPlaintextRejected() {
        // 2048 位 + OAEP-SHA256：明文上限 190 字节
        byte[] tooLong = new byte[191];
        assertThrows(IllegalStateException.class, () -> RsaDemo.oaepEncrypt(pub, tooLong));
    }
}
