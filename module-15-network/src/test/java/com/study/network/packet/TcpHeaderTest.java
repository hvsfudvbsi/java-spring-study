package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP 首部测试：验证编码/解析往返、位字段提取和首部长度计算。
 */
class TcpHeaderTest {

    @Test
    @DisplayName("SYN 报文：端口/序号/标志位编码后能完整解析回来")
    void synPacketRoundTrip() {
        TcpHeader original = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false,
                65535, 0, 0);

        byte[] bytes = original.encode();
        assertEquals(20, bytes.length, "无选项 TCP 首部固定 20 字节");

        TcpHeader parsed = TcpHeader.parse(bytes);
        assertEquals(12345, parsed.sourcePort());
        assertEquals(80, parsed.destinationPort());
        assertEquals(1000, parsed.sequenceNumber());
        assertEquals(0, parsed.acknowledgmentNumber());
        assertTrue(parsed.syn(), "SYN 标志应为 true");
        assertFalse(parsed.ack(), "SYN 报文不应带 ACK");
        assertFalse(parsed.fin());
        assertEquals(65535, parsed.windowSize());
    }

    @Test
    @DisplayName("ACK+FIN 报文：标志位组合正确解析")
    void ackFinPacketRoundTrip() {
        TcpHeader original = new TcpHeader(50000, 443, 100, 200,
                5, true, false, true, false, false,
                1024, 0, 0);

        TcpHeader parsed = TcpHeader.parse(original.encode());
        assertTrue(parsed.ack());
        assertTrue(parsed.fin(), "FIN 标志应为 true");
        assertFalse(parsed.syn());
        assertEquals(100, parsed.sequenceNumber());
        assertEquals(200, parsed.acknowledgmentNumber());
    }

    @Test
    @DisplayName("数据偏移位字段：dataOffset=6 表示首部 24 字节")
    void dataOffsetDeterminesHeaderLength() {
        TcpHeader withOptions = new TcpHeader(1, 2, 0, 0,
                6, false, false, false, false, false,
                0, 0, 0);

        byte[] bytes = withOptions.encode();
        // 第 12 字节高 4 位 = 6 -> 0x60
        assertEquals(0x60, bytes[12] & 0xFF, "dataOffset 应写入第 12 字节高 4 位");

        TcpHeader parsed = TcpHeader.parse(bytes);
        assertEquals(6, parsed.dataOffset());
        assertEquals(24, parsed.headerLength(), "首部长度 = dataOffset * 4");
    }

    @Test
    @DisplayName("SYN 标志位于第 13 字节 bit1（0x02）")
    void synFlagBitPosition() {
        TcpHeader syn = new TcpHeader(1, 2, 0, 0,
                5, false, true, false, false, false, 0, 0, 0);
        assertEquals(0x02, syn.encode()[13] & 0xFF, "SYN = 0x02");

        TcpHeader fin = new TcpHeader(1, 2, 0, 0,
                5, false, false, true, false, false, 0, 0, 0);
        assertEquals(0x01, fin.encode()[13] & 0xFF, "FIN = 0x01");
    }

    @Test
    @DisplayName("字节数不足 20 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        byte[] tooShort = new byte[10];
        assertThrows(IllegalArgumentException.class, () -> TcpHeader.parse(tooShort));
    }

    @Test
    @DisplayName("带偏移解析：从完整报文中间位置提取 TCP 首部")
    void parseWithOffset() {
        TcpHeader original = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        // 前面放 5 字节填充，TCP 首部从偏移 5 开始
        byte[] padded = new byte[5 + 20];
        System.arraycopy(original.encode(), 0, padded, 5, 20);

        TcpHeader parsed = TcpHeader.parse(padded, 5);
        assertEquals(12345, parsed.sourcePort());
        assertEquals(80, parsed.destinationPort());
        assertTrue(parsed.syn());
        assertArrayEquals(original.encode(), parsed.encode());
    }
}
