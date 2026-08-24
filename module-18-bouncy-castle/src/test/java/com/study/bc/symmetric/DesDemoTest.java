package com.study.bc.symmetric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DesDemoTest {

    private static final byte[] PLAIN = "3DES 测试：12345678".getBytes(StandardCharsets.UTF_8);

    @Test
    void desRoundTrip() {
        byte[] key = DesDemo.randomDesKey();
        assertEquals(8, key.length);
        byte[] cipher = DesDemo.desCbcEncrypt(key, PLAIN);
        assertArrayEquals(PLAIN, DesDemo.desCbcDecrypt(key, cipher));
    }

    @Test
    void desedeRoundTrip() {
        byte[] key = DesDemo.randomDesedeKey();
        assertEquals(24, key.length);
        byte[] cipher = DesDemo.desedeCbcEncrypt(key, PLAIN);
        assertArrayEquals(PLAIN, DesDemo.desedeCbcDecrypt(key, cipher));
    }

    @Test
    void wrongKeyFails() {
        byte[] key = DesDemo.randomDesKey();
        byte[] cipher = DesDemo.desCbcEncrypt(key, PLAIN);
        assertThrows(IllegalStateException.class,
                () -> DesDemo.desCbcDecrypt(DesDemo.randomDesKey(), cipher));
    }
}
