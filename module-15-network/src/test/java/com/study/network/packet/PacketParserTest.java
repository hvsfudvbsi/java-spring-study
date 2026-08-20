package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完整报文分层解析测试：以太网帧 + IP + TCP/UDP + 负载。
 */
class PacketParserTest {

    /** 拼接字节数组 */
    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    @Test
    @DisplayName("以太网+IP+TCP 报文：逐层解析出三个首部与负载")
    void parseTcpPacketLayers() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("FF:FF:FF:FF:FF:FF"),
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"),
                EthernetFrame.ETHERTYPE_IPV4);
        IpHeader ip = new IpHeader(4, 5, 20 + 20, 1, 64,
                IpHeader.PROTOCOL_TCP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("93.184.216.34"));
        TcpHeader tcp = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        byte[] payload = "GET / HTTP/1.1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] frame = concat(eth.encode(), ip.encode(), tcp.encode(), payload);
        PacketParser.ParsedPacket parsed = PacketParser.parse(frame);

        // 以太网层
        assertEquals(0x0800, parsed.ethernet().etherType());
        // IP 层
        assertEquals(4, parsed.ip().version());
        assertEquals(IpHeader.PROTOCOL_TCP, parsed.ip().protocol());
        assertEquals("192.168.1.10", IpHeader.toIpString(parsed.ip().sourceIp()));
        // TCP 层
        assertTrue(parsed.isTcp());
        TcpHeader parsedTcp = (TcpHeader) parsed.transport();
        assertEquals(12345, parsedTcp.sourcePort());
        assertEquals(80, parsedTcp.destinationPort());
        assertTrue(parsedTcp.syn());
        // 负载
        assertEquals(payload.length, parsed.payloadLength());
    }

    @Test
    @DisplayName("以太网+IP+UDP 报文：协议号 17 分派到 UDP")
    void parseUdpPacketLayers() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("00:11:22:33:44:55"),
                EthernetFrame.parseMac("66:77:88:99:AA:BB"),
                EthernetFrame.ETHERTYPE_IPV4);
        IpHeader ip = new IpHeader(4, 5, 20 + 8 + 12, 1, 64,
                IpHeader.PROTOCOL_UDP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("8.8.8.8"));
        UdpHeader udp = new UdpHeader(53000, 53, 8 + 12, 0);
        byte[] dnsPayload = "query-example".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] frame = concat(eth.encode(), ip.encode(), udp.encode(), dnsPayload);
        PacketParser.ParsedPacket parsed = PacketParser.parse(frame);

        assertTrue(parsed.isUdp());
        UdpHeader parsedUdp = (UdpHeader) parsed.transport();
        assertEquals(53, parsedUdp.destinationPort(), "DNS 默认端口 53");
        assertEquals(12, parsedUdp.payloadLength());
    }

    @Test
    @DisplayName("以太网+IP+ICMP 报文：协议号 1 分派到 ICMP（ping 请求）")
    void parseIcmpPacketLayers() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("00:11:22:33:44:55"),
                EthernetFrame.parseMac("66:77:88:99:AA:BB"),
                EthernetFrame.ETHERTYPE_IPV4);
        IpHeader ip = new IpHeader(4, 5, 20 + 8, 1, 64,
                IpHeader.PROTOCOL_ICMP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("8.8.8.8"));
        IcmpHeader icmp = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0,
                0x1234, 0x0001, 1);

        byte[] frame = concat(eth.encode(), ip.encode(), icmp.encode());
        PacketParser.ParsedPacket parsed = PacketParser.parse(frame);

        assertEquals(IpHeader.PROTOCOL_ICMP, parsed.ip().protocol());
        assertTrue(parsed.isIcmp());
        IcmpHeader parsedIcmp = (IcmpHeader) parsed.transport();
        assertEquals(IcmpHeader.TYPE_ECHO_REQUEST, parsedIcmp.type(), "ping 请求类型 8");
        assertEquals(0, parsedIcmp.code());
        assertEquals(0, parsed.payloadLength(), "无负载时 ICMP 数据为 0 字节");
    }

    @Test
    @DisplayName("未知协议号抛 IllegalArgumentException")
    void parseRejectsUnknownProtocol() {
        IpHeader ip = new IpHeader(4, 5, 20 + 20, 1, 64, 99, 0, 0, 0);
        byte[] frame = concat(new byte[14], ip.encode(), new byte[20]);
        assertThrows(IllegalArgumentException.class, () -> PacketParser.parse(frame));
    }
}
