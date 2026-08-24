package com.study.bc.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HmacDemoTest {

    private static final byte[] KEY = HmacDemo.randomKey(32);
    private static final byte[] DATA = "HMAC 测试数据".getBytes(StandardCharsets.UTF_8);

    @Test
    void sizes() {
        assertEquals(16, HmacDemo.hmac("HmacMD5", KEY, DATA).length);
        assertEquals(32, HmacDemo.hmac("HmacSHA256", KEY, DATA).length);
        assertEquals(64, HmacDemo.hmac("HmacSHA512", KEY, DATA).length);
    }

    @Test
    void deterministic() {
        assertTrue(java.util.Arrays.equals(HmacDemo.hmac("HmacSHA256", KEY, DATA),
                HmacDemo.hmac("HmacSHA256", KEY, DATA)));
    }

    @Test
    void wrongKeyDiffers() {
        byte[] tag = HmacDemo.hmac("HmacSHA256", KEY, DATA);
        byte[] wrong = HmacDemo.hmac("HmacSHA256", HmacDemo.randomKey(32), DATA);
        assertFalse(java.util.Arrays.equals(tag, wrong));
    }

    @Test
    void unknownAlgorithmRejected() {
        assertThrows(IllegalArgumentException.class, () -> HmacDemo.hmac("NOPE", KEY, DATA));
    }
}
