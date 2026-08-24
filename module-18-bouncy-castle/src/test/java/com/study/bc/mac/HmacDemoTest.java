package com.study.bc.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacDemoTest {

    private static final byte[] KEY = HmacDemo.randomKey(32);
    private static final byte[] DATA = "HMAC 测试数据".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("HMAC 标签长度：MD5=16、SHA-256=32、SHA-512=64 字节")
    void sizes() {
        assertEquals(16, HmacDemo.hmac("HmacMD5", KEY, DATA).length);
        assertEquals(32, HmacDemo.hmac("HmacSHA256", KEY, DATA).length);
        assertEquals(64, HmacDemo.hmac("HmacSHA512", KEY, DATA).length);
    }

    @Test
    @DisplayName("HMAC 确定性：同密钥同数据两次计算一致")
    void deterministic() {
        assertTrue(java.util.Arrays.equals(HmacDemo.hmac("HmacSHA256", KEY, DATA),
                HmacDemo.hmac("HmacSHA256", KEY, DATA)));
    }

    @Test
    @DisplayName("HMAC 错钥：不同密钥算出不同 MAC")
    void wrongKeyDiffers() {
        byte[] tag = HmacDemo.hmac("HmacSHA256", KEY, DATA);
        byte[] wrong = HmacDemo.hmac("HmacSHA256", HmacDemo.randomKey(32), DATA);
        assertFalse(java.util.Arrays.equals(tag, wrong));
    }

    @Test
    @DisplayName("HMAC 未知算法：抛 IllegalArgumentException")
    void unknownAlgorithmRejected() {
        assertThrows(IllegalArgumentException.class, () -> HmacDemo.hmac("NOPE", KEY, DATA));
    }
}
