package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ARP 报文测试：验证 28 字节固定格式、请求/回复操作码与字段位置。
 */
class ArpHeaderTest {

    @Test
    @DisplayName("ARP 请求往返：广播询问「谁是 192.168.1.1」，编码后能完整解析回来")
    void requestRoundTrip() {
        ArpHeader request = new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REQUEST,
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"), IpHeader.parseIp("192.168.1.10"),
                EthernetFrame.parseMac("00:00:00:00:00:00"), IpHeader.parseIp("192.168.1.1"));

        byte[] bytes = request.encode();
        assertEquals(28, bytes.length, "以太网+IPv4 的 ARP 报文固定 28 字节");

        ArpHeader parsed = ArpHeader.parse(bytes);
        assertEquals(ArpHeader.HARDWARE_ETHERNET, parsed.hardwareType());
        assertEquals(ArpHeader.PROTOCOL_IPV4, parsed.protocolType());
        assertEquals(6, parsed.hardwareSize());
        assertEquals(4, parsed.protocolSize());
        assertEquals(ArpHeader.OPCODE_REQUEST, parsed.opcode());
        assertEquals("ARP Request（广播询问）", parsed.opcodeName());
        assertEquals("AA:BB:CC:DD:EE:FF", EthernetFrame.toMacString(parsed.senderMac()));
        assertEquals("192.168.1.10", IpHeader.toIpString(parsed.senderIp()));
        assertEquals("192.168.1.1", IpHeader.toIpString(parsed.targetIp()));
        assertArrayEquals(request.encode(), parsed.encode());
    }

    @Test
    @DisplayName("ARP 回复往返：192.168.1.1 应答自己的 MAC")
    void replyRoundTrip() {
        ArpHeader reply = new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REPLY,
                EthernetFrame.parseMac("11:22:33:44:55:66"), IpHeader.parseIp("192.168.1.1"),
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"), IpHeader.parseIp("192.168.1.10"));

        ArpHeader parsed = ArpHeader.parse(reply.encode());
        assertEquals(ArpHeader.OPCODE_REPLY, parsed.opcode());
        assertEquals("ARP Reply（应答）", parsed.opcodeName());
        assertEquals("11:22:33:44:55:66", EthernetFrame.toMacString(parsed.senderMac()));
        assertEquals("192.168.1.1", IpHeader.toIpString(parsed.senderIp()));
    }

    @Test
    @DisplayName("字段位置：硬件类型/协议类型/长度/操作码/四个地址按序排列")
    void fieldPositions() {
        ArpHeader request = new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REQUEST,
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"), IpHeader.parseIp("192.168.1.10"),
                EthernetFrame.parseMac("00:00:00:00:00:00"), IpHeader.parseIp("192.168.1.1"));
        byte[] bytes = request.encode();

        assertEquals(0x00, bytes[0] & 0xFF);
        assertEquals(0x01, bytes[1] & 0xFF, "硬件类型=1 以太网");
        assertEquals(0x08, bytes[2] & 0xFF);
        assertEquals(0x00, bytes[3] & 0xFF, "协议类型=0x0800 IPv4");
        assertEquals(6, bytes[4] & 0xFF, "MAC 地址长度 6");
        assertEquals(4, bytes[5] & 0xFF, "IP 地址长度 4");
        assertEquals(0x00, bytes[6] & 0xFF);
        assertEquals(0x01, bytes[7] & 0xFF, "操作码=1 请求");
        assertEquals("AA:BB:CC:DD:EE:FF", EthernetFrame.toMacString(
                java.util.Arrays.copyOfRange(bytes, 8, 14)), "发送方 MAC 在偏移 8");
        assertEquals("192.168.1.10", IpHeader.toIpString(
                (int) (((long) (bytes[14] & 0xFF) << 24) | ((long) (bytes[15] & 0xFF) << 16)
                        | ((bytes[16] & 0xFF) << 8) | (bytes[17] & 0xFF))), "发送方 IP 在偏移 14");
    }

    @Test
    @DisplayName("带偏移解析：从 14 字节以太网帧头之后提取 ARP 报文")
    void parseWithOffset() {
        ArpHeader original = new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REQUEST,
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"), IpHeader.parseIp("192.168.1.10"),
                EthernetFrame.parseMac("00:00:00:00:00:00"), IpHeader.parseIp("192.168.1.1"));
        byte[] padded = new byte[14 + 28]; // 模拟以太网帧头 14 字节 + ARP 28 字节
        System.arraycopy(original.encode(), 0, padded, 14, 28);

        ArpHeader parsed = ArpHeader.parse(padded, 14);
        assertEquals(ArpHeader.OPCODE_REQUEST, parsed.opcode());
        assertEquals("192.168.1.1", IpHeader.toIpString(parsed.targetIp()));
    }

    @Test
    @DisplayName("字节数不足 28 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> ArpHeader.parse(new byte[27]));
    }

    @Test
    @DisplayName("MAC 地址长度不为 6 字节时构造抛 IllegalArgumentException")
    void constructorRejectsBadMac() {
        assertThrows(IllegalArgumentException.class, () -> new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REQUEST,
                new byte[5], 0, new byte[6], 0));
    }
}
