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

    // ============ CBC 密文块翻转攻击 ============

    /** 17 字节明文 → 32 字节（2 块）：块1="name=admin&role"，块2="=0"+填充。 */
    private static final byte[] VICTIM = "name=admin&role=0".getBytes(StandardCharsets.UTF_8);

    @Test
    void cbcBitFlipChangesTargetByte() {
        byte[] cbc = AesDemo.cbcEncrypt(KEY, VICTIM);
        // 明文 "name=admin&role=0"：块1="name=admin&role="(16B)，'0' 在块 2 偏移 0。
        // 翻转 C1[0] ^= ('0'^'1') → P2[0] 由 '0' 变 '1'（可控修改）
        byte[] tampered = AesDemo.cbcBitFlip(cbc, 1, 0, (byte) ('0' ^ '1'));
        byte[] result = AesDemo.cbcDecrypt(KEY, tampered);
        assertEquals('1', result[16], "目标字节应被可控修改为 '1'");
    }

    @Test
    void cbcBitFlipCorruptsPreviousBlock() {
        byte[] cbc = AesDemo.cbcEncrypt(KEY, VICTIM);
        byte[] tampered = AesDemo.cbcBitFlip(cbc, 1, 0, (byte) ('0' ^ '1'));
        byte[] result = AesDemo.cbcDecrypt(KEY, tampered);
        // 被翻转的 C1 解出的块 1 变乱码（与原文块 1 不同）
        String block1 = new String(result, 0, 16, StandardCharsets.UTF_8);
        assertNotEquals("name=admin&role=", block1, "前一块应被破坏");
    }

    @Test
    void cbcBitFlipIvOnlyAffectsFirstBlock() {
        byte[] cbc = AesDemo.cbcEncrypt(KEY, VICTIM);
        // 翻转 IV 偏移 0（'n' 0x6E ^ 'N' 0x4E = 0x20）→ 只影响块 1 对应字节，其余块不受影响
        byte[] tampered = AesDemo.cbcBitFlip(cbc, 0, 0, (byte) ('n' ^ 'N'));
        byte[] result = AesDemo.cbcDecrypt(KEY, tampered);
        assertEquals('N', result[0], "IV 翻转应修改第一块对应字节");
        // 块 2 保持原文（'0' 在偏移 16，解密结果已去填充）
        assertEquals(17, result.length);
        assertEquals('0', result[16]);
    }

    @Test
    void cbcBitFlipGcmRejectsTampering() {
        // 对照：GCM 认证加密下同样的翻转被拒绝（标签校验失败）
        byte[] gcm = AesDemo.gcmEncrypt(KEY, VICTIM);
        byte[] tampered = gcm.clone();
        tampered[12 + 1] ^= (byte) ('0' ^ '1'); // 跳过 12 字节 IV，翻转第一个密文块
        assertThrows(IllegalStateException.class, () -> AesDemo.gcmDecrypt(KEY, tampered));
    }

    @Test
    void cbcBitFlipOutOfRangeRejected() {
        byte[] cbc = AesDemo.cbcEncrypt(KEY, VICTIM);
        assertThrows(IllegalArgumentException.class, () -> AesDemo.cbcBitFlip(cbc, 99, 0, (byte) 1));
        assertThrows(IllegalArgumentException.class, () -> AesDemo.cbcBitFlip(cbc, 0, 16, (byte) 1));
        assertThrows(IllegalArgumentException.class, () -> AesDemo.cbcBitFlip(cbc, -1, 0, (byte) 1));
    }

    @Test
    void cbcBitFlipInvalidFormatRejected() {
        byte[] bad = new byte[10]; // 不是 [IV(16) || 16 倍数]
        assertThrows(IllegalArgumentException.class, () -> AesDemo.cbcBitFlip(bad, 0, 0, (byte) 1));
    }
}
