package com.study.bc.asymmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import org.junit.jupiter.api.Test;

class EccDemoTest {

    @Test
    void ecdhAgreement() {
        KeyPair alice = EccDemo.generateKeyPair();
        KeyPair bob = EccDemo.generateKeyPair();
        byte[] a = EccDemo.ecdh(alice, bob.getPublic());
        byte[] b = EccDemo.ecdh(bob, alice.getPublic());
        assertArrayEquals(a, b);
    }

    @Test
    void signVerify() {
        KeyPair alice = EccDemo.generateKeyPair();
        byte[] data = "ECC 签名测试".getBytes(StandardCharsets.UTF_8);
        byte[] sig = EccDemo.sign(alice, data);
        assertTrue(EccDemo.verify(alice, data, sig));
    }

    @Test
    void tamperedDataRejected() {
        KeyPair alice = EccDemo.generateKeyPair();
        byte[] data = "原始数据".getBytes(StandardCharsets.UTF_8);
        byte[] sig = EccDemo.sign(alice, data);
        assertFalse(EccDemo.verify(alice, "篡改数据".getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void wrongPublicKeyRejected() {
        KeyPair alice = EccDemo.generateKeyPair();
        KeyPair mallory = EccDemo.generateKeyPair();
        byte[] data = "原始数据".getBytes(StandardCharsets.UTF_8);
        byte[] sig = EccDemo.sign(alice, data);
        assertFalse(EccDemo.verify(mallory, data, sig));
    }
}
