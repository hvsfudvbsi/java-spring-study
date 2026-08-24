package com.study.bc.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Sm4DemoTest {

    private static final byte[] KEY = Sm4Demo.randomKey();
    private static final byte[] PLAIN = "国密 SM4 测试：0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("SM4 密钥必须 16 字节（128 位）")
    void keyLength() {
        assertEquals(16, KEY.length);
    }

    @Test
    @DisplayName("SM4-ECB：加密后再解密能还原明文（往返一致）")
    void ecbRoundTrip() {
        byte[] cipher = Sm4Demo.ecbEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, Sm4Demo.ecbDecrypt(KEY, cipher));
    }

    @Test
    @DisplayName("SM4-CBC：加密后再解密能还原明文（往返一致）")
    void cbcRoundTrip() {
        byte[] cipher = Sm4Demo.cbcEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, Sm4Demo.cbcDecrypt(KEY, cipher));
    }

    @Test
    @DisplayName("SM4-GCM：加密后再解密能还原明文（往返一致）")
    void gcmRoundTrip() {
        byte[] cipher = Sm4Demo.gcmEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, Sm4Demo.gcmDecrypt(KEY, cipher));
    }

    @Test
    @DisplayName("SM4-GCM 篡改检测：翻转标签最后 1 位，解密被拒绝")
    void gcmTamperDetected() {
        byte[] cipher = Sm4Demo.gcmEncrypt(KEY, PLAIN);
        byte[] tampered = cipher.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> Sm4Demo.gcmDecrypt(KEY, tampered));
    }

    @Test
    @DisplayName("SM4-CBC 错误密钥：解密抛异常（密钥不匹配）")
    void wrongKeyFails() {
        byte[] cipher = Sm4Demo.cbcEncrypt(KEY, PLAIN);
        assertThrows(IllegalStateException.class, () -> Sm4Demo.cbcDecrypt(Sm4Demo.randomKey(), cipher));
    }
}
