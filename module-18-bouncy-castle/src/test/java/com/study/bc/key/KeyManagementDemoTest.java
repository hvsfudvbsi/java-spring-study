package com.study.bc.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;

import org.junit.jupiter.api.Test;

class KeyManagementDemoTest {

    @Test
    void pemRoundTrip() {
        KeyPair pair = KeyManagementDemo.generateRsa();
        String pem = KeyManagementDemo.pemEncode(pair.getPublic()) + KeyManagementDemo.pemEncode(pair.getPrivate());
        KeyPair restored = KeyManagementDemo.pemDecode(pem);
        assertArrayEquals(pair.getPublic().getEncoded(), restored.getPublic().getEncoded());
        assertArrayEquals(pair.getPrivate().getEncoded(), restored.getPrivate().getEncoded());
    }

    @Test
    void pemFormat() {
        KeyPair pair = KeyManagementDemo.generateRsa();
        String pubPem = KeyManagementDemo.pemEncode(pair.getPublic());
        assertTrue(pubPem.contains("BEGIN PUBLIC KEY"));
        assertTrue(pubPem.contains("END PUBLIC KEY"));
    }

    @Test
    void derBase64Consistent() {
        KeyPair pair = KeyManagementDemo.generateRsa();
        byte[] der = KeyManagementDemo.derEncode(pair.getPublic());
        String b64 = KeyManagementDemo.base64Encode(der);
        assertArrayEquals(der, java.util.Base64.getDecoder().decode(b64));
    }
}
