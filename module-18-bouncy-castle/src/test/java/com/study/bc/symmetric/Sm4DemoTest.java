package com.study.bc.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Sm4DemoTest {

    private static final byte[] KEY = Sm4Demo.randomKey();
    private static final byte[] PLAIN = "国密 SM4 测试：0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void keyLength() {
        assertEquals(16, KEY.length);
    }

    @Test
    void ecbRoundTrip() {
        byte[] cipher = Sm4Demo.ecbEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, Sm4Demo.ecbDecrypt(KEY, cipher));
    }

    @Test
    void cbcRoundTrip() {
        byte[] cipher = Sm4Demo.cbcEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, Sm4Demo.cbcDecrypt(KEY, cipher));
    }

    @Test
    void gcmRoundTrip() {
        byte[] cipher = Sm4Demo.gcmEncrypt(KEY, PLAIN);
        assertArrayEquals(PLAIN, Sm4Demo.gcmDecrypt(KEY, cipher));
    }

    @Test
    void gcmTamperDetected() {
        byte[] cipher = Sm4Demo.gcmEncrypt(KEY, PLAIN);
        byte[] tampered = cipher.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> Sm4Demo.gcmDecrypt(KEY, tampered));
    }

    @Test
    void wrongKeyFails() {
        byte[] cipher = Sm4Demo.cbcEncrypt(KEY, PLAIN);
        assertThrows(IllegalStateException.class, () -> Sm4Demo.cbcDecrypt(Sm4Demo.randomKey(), cipher));
    }
}
