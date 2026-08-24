package com.study.bc.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AesDemoTest {

    private static final byte[] KEY = AesDemo.randomKey(128);
    private static final byte[] PLAIN = "AES 测试数据：1234567890".getBytes(StandardCharsets.UTF_8);

    @Test
    void ecbRoundTrip() {
        byte[] cipher = AesDemo.ecbEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, AesDemo.ecbDecrypt(KEY, cipher));
    }

    @Test
    void cbcRoundTrip() {
        byte[] cipher = AesDemo.cbcEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, AesDemo.cbcDecrypt(KEY, cipher));
    }

    @Test
    void cbcRandomizedIv() {
        // 相同明文两次加密结果不同（IV 随机）
        assertNotEquals(java.util.Arrays.toString(AesDemo.cbcEncrypt(KEY, PLAIN)),
                java.util.Arrays.toString(AesDemo.cbcEncrypt(KEY, PLAIN)));
    }

    @Test
    void ctrRoundTrip() {
        byte[] cipher = AesDemo.ctrEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, AesDemo.ctrDecrypt(KEY, cipher));
    }

    @Test
    void ctrNoPaddingStream() {
        byte[] plain = new byte[37]; // 非 16 倍数，流模式无需填充
        for (int i = 0; i < plain.length; i++) {
            plain[i] = (byte) i;
        }
        byte[] cipher = AesDemo.ctrEncrypt(KEY, plain);
        assertEquals(16 + 37, cipher.length);
        assertArrayEquals(plain, AesDemo.ctrDecrypt(KEY, cipher));
    }

    @Test
    void gcmRoundTrip() {
        byte[] cipher = AesDemo.gcmEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, AesDemo.gcmDecrypt(KEY, cipher));
    }

    @Test
    void gcmTamperDetected() {
        byte[] cipher = AesDemo.gcmEncrypt(KEY, PLAIN);
        byte[] tampered = cipher.clone();
        tampered[tampered.length - 1] ^= 0x01; // 翻转标签
        assertThrows(IllegalStateException.class, () -> AesDemo.gcmDecrypt(KEY, tampered));
    }

    @Test
    void wrongKeyFails() {
        byte[] cipher = AesDemo.cbcEncrypt(KEY, PLAIN);
        byte[] wrongKey = AesDemo.randomKey(128);
        assertThrows(IllegalStateException.class, () -> AesDemo.cbcDecrypt(wrongKey, cipher));
    }
}
