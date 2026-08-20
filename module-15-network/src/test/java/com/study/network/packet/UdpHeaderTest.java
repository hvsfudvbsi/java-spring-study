package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UDP 首部测试：8 字节固定长度、长度字段与负载计算。
 */
class UdpHeaderTest {

    @Test
    @DisplayName("UDP 首部固定 8 字节：编码后能完整解析回来")
    void roundTrip() {
        UdpHeader original = new UdpHeader(53000, 53, 8 + 12, 0);

        byte[] bytes = original.encode();
        assertEquals(8, bytes.length, "UDP 首部固定 8 字节");

        UdpHeader parsed = UdpHeader.parse(bytes);
        assertEquals(53000, parsed.sourcePort());
        assertEquals(53, parsed.destinationPort());
        assertEquals(20, parsed.length(), "总长度 = 8 首部 + 12 负载");
        assertEquals(12, parsed.payloadLength(), "负载长度 = 总长度 - 8");
    }

    @Test
    @DisplayName("大端字节序：端口 0x1234 写入前两个字节")
    void bigEndianOrder() {
        UdpHeader header = new UdpHeader(0x1234, 0x5678, 8, 0);
        byte[] bytes = header.encode();
        assertEquals(0x12, bytes[0] & 0xFF);
        assertEquals(0x34, bytes[1] & 0xFF);
        assertEquals(0x56, bytes[2] & 0xFF);
        assertEquals(0x78, bytes[3] & 0xFF);
    }

    @Test
    @DisplayName("字节数不足 8 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> UdpHeader.parse(new byte[7]));
    }
}
