package com.study.bc.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyAgreementDemoTest {

    @Test
    @DisplayName("DH-2048 协商：双方算出共享秘密且一致")
    void dhAgreement() {
        // 用生成的 DH 参数跑协商（内部随机密钥对）
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("DH");
            kpg.initialize(2048, new java.security.SecureRandom());
            java.security.KeyPair alice = kpg.generateKeyPair();
            javax.crypto.interfaces.DHPublicKey dhPub = (javax.crypto.interfaces.DHPublicKey) alice.getPublic();
            byte[] secret = KeyAgreementDemo.dhAgree(dhPub.getParams());
            assertTrue(secret.length > 0, "DH 共享秘密双方应一致");
        } catch (Exception e) {
            throw new IllegalStateException("DH 测试失败", e);
        }
    }

    @Test
    @DisplayName("ECDH P-256 协商：共享秘密 32 字节且双方一致")
    void ecdhAgreement() {
        byte[] secret = KeyAgreementDemo.ecdhAgree();
        assertTrue(secret.length > 0, "ECDH 共享秘密双方应一致");
        assertEquals(32, secret.length); // P-256 共享秘密 32 字节
    }
}
