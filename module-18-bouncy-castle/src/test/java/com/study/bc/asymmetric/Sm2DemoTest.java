package com.study.bc.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Sm2DemoTest {

    private static ECPublicKeyParameters pub;
    private static ECPrivateKeyParameters priv;

    @BeforeAll
    static void setUp() {
        AsymmetricCipherKeyPair pair = Sm2Demo.generateKeyPair();
        pub = (ECPublicKeyParameters) pair.getPublic();
        priv = (ECPrivateKeyParameters) pair.getPrivate();
    }

    @Test
    void encryptDecryptRoundTrip() {
        byte[] plain = "国密 SM2 加解密测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = Sm2Demo.encrypt(pub, plain);
        assertArrayEquals(plain, Sm2Demo.decrypt(priv, cipher));
    }

    @Test
    void encryptedRandomized() {
        byte[] plain = "随机性测试".getBytes(StandardCharsets.UTF_8);
        byte[] c1 = Sm2Demo.encrypt(pub, plain);
        byte[] c2 = Sm2Demo.encrypt(pub, plain);
        assertFalse(java.util.Arrays.equals(c1, c2));
    }

    @Test
    void signVerify() {
        byte[] data = "国密 SM2 签名测试".getBytes(StandardCharsets.UTF_8);
        byte[] sig = Sm2Demo.sign(priv, data);
        assertTrue(Sm2Demo.verify(pub, data, sig));
    }

    @Test
    void tamperedDataRejected() {
        byte[] data = "原始数据".getBytes(StandardCharsets.UTF_8);
        byte[] sig = Sm2Demo.sign(priv, data);
        assertFalse(Sm2Demo.verify(pub, "篡改数据".getBytes(StandardCharsets.UTF_8), sig));
    }
}
