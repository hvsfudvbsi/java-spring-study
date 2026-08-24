package com.study.bc.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CmacDemoTest {

    @Test
    @DisplayName("AES-CMAC 标签固定 16 字节（128 位）")
    void tagSize() {
        byte[] key = new byte[16];
        byte[] tag = CmacDemo.cmac(key, "数据".getBytes(StandardCharsets.UTF_8));
        assertEquals(16, tag.length);
    }

    @Test
    @DisplayName("CMAC 确定性：同密钥同数据两次计算一致")
    void deterministic() {
        byte[] key = new byte[16];
        byte[] data = "CMAC 测试".getBytes(StandardCharsets.UTF_8);
        assertTrue(java.util.Arrays.equals(CmacDemo.cmac(key, data), CmacDemo.cmac(key, data)));
    }

    @Test
    @DisplayName("CMAC 错钥：不同密钥算出不同标签")
    void wrongKeyDiffers() {
        byte[] key = new byte[16];
        SecureRandom random = new SecureRandom();
        byte[] wrongKey = new byte[16];
        random.nextBytes(wrongKey);
        byte[] data = "CMAC 测试".getBytes(StandardCharsets.UTF_8);
        assertFalse(java.util.Arrays.equals(CmacDemo.cmac(key, data), CmacDemo.cmac(wrongKey, data)));
    }
}
