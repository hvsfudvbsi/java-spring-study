package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ICMP 首部测试：类型/代码/校验和字段、ping Echo 报文。
 */
class IcmpHeaderTest {

    @Test
    @DisplayName("ping 请求报文（类型 8）：编码后能完整解析回来")
    void echoRequestRoundTrip() {
        IcmpHeader original = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0,
                0x1234, 0x0001, 1);

        byte[] bytes = original.encode();
        assertEquals(8, bytes.length, "ICMP 首部固定 8 字节");

        IcmpHeader parsed = IcmpHeader.parse(bytes);
        assertEquals(IcmpHeader.TYPE_ECHO_REQUEST, parsed.type());
        assertEquals(0, parsed.code());
        assertEquals(0x1234, parsed.checksum());
        assertEquals(0x0001, parsed.identifier());
        assertEquals(1, parsed.sequence());
    }

    @Test
    @DisplayName("ping 回复报文（类型 0）：Echo Reply")
    void echoReplyRoundTrip() {
        IcmpHeader original = new IcmpHeader(IcmpHeader.TYPE_ECHO_REPLY, 0,
                0xABCD, 0x0001, 1);

        IcmpHeader parsed = IcmpHeader.parse(original.encode());
        assertEquals(IcmpHeader.TYPE_ECHO_REPLY, parsed.type());
        assertEquals("Echo Reply (ping 成功)", parsed.typeName());
    }

    @Test
    @DisplayName("首部字段位置：类型第 0 字节、代码第 1 字节、校验和 2-3 字节")
    void fieldPositions() {
        IcmpHeader icmp = new IcmpHeader(IcmpHeader.TYPE_TIME_EXCEEDED, 1,
                0x1234, 0, 0);
        byte[] bytes = icmp.encode();

        assertEquals(11, bytes[0] & 0xFF, "第 0 字节 = 类型");
        assertEquals(1, bytes[1] & 0xFF, "第 1 字节 = 代码");
        assertEquals(0x12, bytes[2] & 0xFF, "校验和高字节");
        assertEquals(0x34, bytes[3] & 0xFF, "校验和低字节");
    }

    @Test
    @DisplayName("类型名称：常用类型有可读名称")
    void typeNames() {
        assertEquals("Echo Request (ping 请求)",
                new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0, 0, 0, 0).typeName());
        assertEquals("Destination Unreachable",
                new IcmpHeader(IcmpHeader.TYPE_DESTINATION_UNREACHABLE, 0, 0, 0, 0).typeName());
        assertEquals("Time Exceeded (TTL 耗尽)",
                new IcmpHeader(IcmpHeader.TYPE_TIME_EXCEEDED, 0, 0, 0, 0).typeName());
        assertEquals("未知类型 99",
                new IcmpHeader(99, 0, 0, 0, 0).typeName());
    }

    @Test
    @DisplayName("字节数不足 8 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> IcmpHeader.parse(new byte[7]));
    }

    @Test
    @DisplayName("带偏移解析：从完整报文中间位置提取 ICMP 首部")
    void parseWithOffset() {
        IcmpHeader original = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0,
                0x1111, 0x2222, 3);
        byte[] padded = new byte[14 + 8]; // 模拟以太网帧头 14 字节 + ICMP 8 字节
        System.arraycopy(original.encode(), 0, padded, 14, 8);

        IcmpHeader parsed = IcmpHeader.parse(padded, 14);
        assertEquals(IcmpHeader.TYPE_ECHO_REQUEST, parsed.type());
        assertEquals(0x2222, parsed.identifier());
        assertEquals(3, parsed.sequence());
    }

    // ---- ICMP 校验和（RFC 1071：首部 + 数据） ----

    @Test
    @DisplayName("校验和往返：computeChecksum 覆盖首部+数据，withValidChecksum 后 verify 通过")
    void checksumRoundTrip() {
        byte[] payload = "ping-payload-1234".getBytes(); // 16 字节偶数
        IcmpHeader icmp = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0, 0, 0x0001, 1);
        int checksum = icmp.computeChecksum(payload);
        assertTrue(checksum != 0, "校验和不应为 0");

        IcmpHeader valid = icmp.withValidChecksum(payload);
        assertEquals(checksum, valid.checksum());
        assertTrue(valid.verify(payload), "整体反码和应为 0xFFFF");
    }

    @Test
    @DisplayName("篡改数据后校验失败：校验和能发现数据被改")
    void checksumDetectsTampering() {
        byte[] payload = "ping-payload-1234".getBytes();
        IcmpHeader valid = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0, 0, 1, 1)
                .withValidChecksum(payload);
        assertTrue(valid.verify(payload));

        byte[] tampered = payload.clone();
        tampered[0] ^= 0x01; // 翻转一个 bit
        assertFalse(valid.verify(tampered), "数据被篡改后校验必须失败");
    }

    @Test
    @DisplayName("奇数长度数据也能校验（末尾补 0 参与计算）")
    void checksumWithOddLengthPayload() {
        IcmpHeader valid = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0, 0, 1, 1)
                .withValidChecksum("abc".getBytes()); // 3 字节奇数
        assertTrue(valid.verify("abc".getBytes()));
    }
}
