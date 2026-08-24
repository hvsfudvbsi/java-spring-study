package com.study.bc.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyManagementDemoTest {

    @Test
    @DisplayName("PEM 往返：公钥+私钥编码后再解析，字节级一致")
    void pemRoundTrip() {
        KeyPair pair = KeyManagementDemo.generateRsa();
        String pem = KeyManagementDemo.pemEncode(pair.getPublic()) + KeyManagementDemo.pemEncode(pair.getPrivate());
        KeyPair restored = KeyManagementDemo.pemDecode(pem);
        assertArrayEquals(pair.getPublic().getEncoded(), restored.getPublic().getEncoded());
        assertArrayEquals(pair.getPrivate().getEncoded(), restored.getPrivate().getEncoded());
    }

    @Test
    @DisplayName("PEM 格式：含 BEGIN/END PUBLIC KEY 标记")
    void pemFormat() {
        KeyPair pair = KeyManagementDemo.generateRsa();
        String pubPem = KeyManagementDemo.pemEncode(pair.getPublic());
        assertTrue(pubPem.contains("BEGIN PUBLIC KEY"));
        assertTrue(pubPem.contains("END PUBLIC KEY"));
    }

    @Test
    @DisplayName("DER/Base64 一致：Base64 解码后还原 DER 字节")
    void derBase64Consistent() {
        KeyPair pair = KeyManagementDemo.generateRsa();
        byte[] der = KeyManagementDemo.derEncode(pair.getPublic());
        String b64 = KeyManagementDemo.base64Encode(der);
        assertArrayEquals(der, java.util.Base64.getDecoder().decode(b64));
    }
}
