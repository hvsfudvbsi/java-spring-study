package com.study.bc.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HashDemoTest {

    private static final byte[] DATA = "Bouncy Castle".getBytes(StandardCharsets.UTF_8);

    @Test
    void knownVectors() {
        // 空串的标准摘要值（与 openssl 一致）
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HexUtil.hex(HashDemo.hash("MD5", new byte[0])));
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709",
                HexUtil.hex(HashDemo.hash("SHA-1", new byte[0])));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HexUtil.hex(HashDemo.hash("SHA-256", new byte[0])));
    }

    @Test
    void digestSizes() {
        assertEquals(16, HashDemo.hash("MD5", DATA).length);
        assertEquals(20, HashDemo.hash("SHA-1", DATA).length);
        assertEquals(32, HashDemo.hash("SHA-256", DATA).length);
        assertEquals(32, HashDemo.hash("SHA3-256", DATA).length);
        assertEquals(32, HashDemo.hash("SM3", DATA).length);
    }

    @Test
    void deterministicAndDifferent() {
        assertTrue(java.util.Arrays.equals(HashDemo.hash("SM3", DATA), HashDemo.hash("SM3", DATA)));
        assertFalse(java.util.Arrays.equals(HashDemo.hash("MD5", DATA), HashDemo.hash("SHA-256", DATA)));
        assertFalse(java.util.Arrays.equals(HashDemo.hash("SHA-256", DATA),
                HashDemo.hash("SHA-256", "x".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void unknownAlgorithmRejected() {
        assertNull(HashDemo.digest("NOPE"));
        assertThrows(IllegalArgumentException.class, () -> HashDemo.hash("NOPE", DATA));
    }

    @Test
    void saltedHashRoundTrip() {
        byte[] salted = HashDemo.saltedHash(DATA);
        assertEquals(48, salted.length); // 16 盐 + 32 摘要
        assertTrue(HashDemo.verifySaltedHash(DATA, salted));
        assertFalse(HashDemo.verifySaltedHash("其他内容".getBytes(StandardCharsets.UTF_8), salted));
    }

    @Test
    void saltedHashRandomized() {
        // 相同输入两次加盐结果不同（盐随机）
        assertNotEquals(HexUtil.hex(HashDemo.saltedHash(DATA)), HexUtil.hex(HashDemo.saltedHash(DATA)));
    }

    /** 测试用 hex 工具（避免与生产类耦合）。 */
    static final class HexUtil {
        private static final char[] HEX = "0123456789abcdef".toCharArray();

        static String hex(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return sb.toString();
        }
    }
}
