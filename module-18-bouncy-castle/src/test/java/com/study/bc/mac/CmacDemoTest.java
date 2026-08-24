package com.study.bc.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class CmacDemoTest {

    @Test
    void tagSize() {
        byte[] key = new byte[16];
        byte[] tag = CmacDemo.cmac(key, "数据".getBytes(StandardCharsets.UTF_8));
        assertEquals(16, tag.length);
    }

    @Test
    void deterministic() {
        byte[] key = new byte[16];
        byte[] data = "CMAC 测试".getBytes(StandardCharsets.UTF_8);
        assertTrue(java.util.Arrays.equals(CmacDemo.cmac(key, data), CmacDemo.cmac(key, data)));
    }

    @Test
    void wrongKeyDiffers() {
        byte[] key = new byte[16];
        SecureRandom random = new SecureRandom();
        byte[] wrongKey = new byte[16];
        random.nextBytes(wrongKey);
        byte[] data = "CMAC 测试".getBytes(StandardCharsets.UTF_8);
        assertFalse(java.util.Arrays.equals(CmacDemo.cmac(key, data), CmacDemo.cmac(wrongKey, data)));
    }
}
