package com.study.bc.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EccDemoTest {

    @Test
    @DisplayName("ECDH 协商：双方各自算出的共享秘密一致")
    void ecdhAgreement() {
        KeyPair alice = EccDemo.generateKeyPair();
        KeyPair bob = EccDemo.generateKeyPair();
        byte[] a = EccDemo.ecdh(alice, bob.getPublic());
        byte[] b = EccDemo.ecdh(bob, alice.getPublic());
        assertArrayEquals(a, b);
    }

    @Test
    @DisplayName("ECDSA 签名：私钥签名后公钥验签通过")
    void signVerify() {
        KeyPair alice = EccDemo.generateKeyPair();
        byte[] data = "ECC 签名测试".getBytes(StandardCharsets.UTF_8);
        byte[] sig = EccDemo.sign(alice, data);
        assertTrue(EccDemo.verify(alice, data, sig));
    }

    @Test
    @DisplayName("ECDSA 篡改检测：签名后修改数据，验签失败")
    void tamperedDataRejected() {
        KeyPair alice = EccDemo.generateKeyPair();
        byte[] data = "原始数据".getBytes(StandardCharsets.UTF_8);
        byte[] sig = EccDemo.sign(alice, data);
        assertFalse(EccDemo.verify(alice, "篡改数据".getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    @DisplayName("ECDSA 错公钥：他人公钥验签失败")
    void wrongPublicKeyRejected() {
        KeyPair alice = EccDemo.generateKeyPair();
        KeyPair mallory = EccDemo.generateKeyPair();
        byte[] data = "原始数据".getBytes(StandardCharsets.UTF_8);
        byte[] sig = EccDemo.sign(alice, data);
        assertFalse(EccDemo.verify(mallory, data, sig));
    }
}
