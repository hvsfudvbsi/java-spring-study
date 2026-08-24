package com.study.bc.signature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import org.junit.jupiter.api.Test;

class SignatureDemoTest {

    private static final byte[] DATA = "数字签名测试数据".getBytes(StandardCharsets.UTF_8);

    private void assertSignVerify(String keyAlgo, int keySize, String jceAlgo) {
        KeyPair pair = SignatureDemo.generateKeyPair(keyAlgo, keySize);
        String sig = SignatureDemo.sign(jceAlgo, pair, DATA);
        assertTrue(SignatureDemo.verify(jceAlgo, pair, DATA, sig), jceAlgo + " 验签失败");
        assertFalse(SignatureDemo.verify(jceAlgo, pair, (DATA + "!").getBytes(StandardCharsets.UTF_8), sig),
                jceAlgo + " 篡改数据不应通过");
    }

    @Test
    void rsaSha256() {
        assertSignVerify("RSA", 2048, "SHA256withRSA");
    }

    @Test
    void ecdsa() {
        assertSignVerify("EC", 0, "SHA256withECDSA");
    }

    @Test
    void dsa() {
        assertSignVerify("DSA", 2048, "SHA256withDSA");
    }

    @Test
    void ed25519() {
        assertSignVerify("Ed25519", 0, "Ed25519");
    }

    @Test
    void sm3withSm2() {
        assertSignVerify("EC", 0, "SM3withSM2");
    }
}
