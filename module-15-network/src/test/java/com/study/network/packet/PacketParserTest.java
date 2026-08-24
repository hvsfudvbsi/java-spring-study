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
    @DisplayName("未知 IP 协议号抛 IllegalArgumentException")
    void parseRejectsUnknownProtocol() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("00:11:22:33:44:55"),
                EthernetFrame.parseMac("66:77:88:99:AA:BB"),
                EthernetFrame.ETHERTYPE_IPV4);
        IpHeader ip = new IpHeader(4, 5, 20 + 20, 1, 64, 99, 0, 0, 0); // 协议号 99 未定义
        byte[] frame = concat(eth.encode(), ip.encode(), new byte[20]);
        assertThrows(IllegalArgumentException.class, () -> PacketParser.parse(frame));
    }

    // ---- 按 EtherType 分派 + ARP ----

    @Test
    @DisplayName("ARP 请求帧：EtherType=0x0806 分派到 ARP，不经过 IP 层")
    void parseArpRequest() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("FF:FF:FF:FF:FF:FF"), // 广播
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"),
                EthernetFrame.ETHERTYPE_ARP);
        ArpHeader arp = new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REQUEST,
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"), IpHeader.parseIp("192.168.1.10"),
                EthernetFrame.parseMac("00:00:00:00:00:00"), IpHeader.parseIp("192.168.1.1"));

        byte[] frame = concat(eth.encode(), arp.encode());
        PacketParser.ParsedPacket parsed = PacketParser.parse(frame);

        assertEquals(EthernetFrame.ETHERTYPE_ARP, parsed.ethernet().etherType());
        assertTrue(parsed.isArp(), "应按 EtherType 识别为 ARP");
        assertEquals(null, parsed.ip(), "ARP 不经过 IP 层，ip() 为 null");
        ArpHeader parsedArp = (ArpHeader) parsed.transport();
        assertEquals(ArpHeader.OPCODE_REQUEST, parsedArp.opcode());
        assertEquals("192.168.1.1", IpHeader.toIpString(parsedArp.targetIp()));
        assertEquals(0, parsed.payloadLength());
    }

    @Test
    @DisplayName("未知 EtherType（如 IPv6 0x86DD）抛 IllegalArgumentException")
    void parseRejectsUnknownEtherType() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("00:11:22:33:44:55"),
                EthernetFrame.parseMac("66:77:88:99:AA:BB"),
                0x86DD); // IPv6：本模块暂不支持
        byte[] frame = concat(eth.encode(), new byte[20]);
        assertThrows(IllegalArgumentException.class, () -> PacketParser.parse(frame));
    }

    // ---- 构造真实应用层报文：IP + UDP + DNS 查询 ----

    @Test
    @DisplayName("完整 DNS 查询报文：以太网+IP+UDP+DNS（头部+查询记录），解析出 53 端口与域名")
    void parseDnsQueryPacket() {
        EthernetFrame eth = new EthernetFrame(
                EthernetFrame.parseMac("00:11:22:33:44:55"),
                EthernetFrame.parseMac("66:77:88:99:AA:BB"),
                EthernetFrame.ETHERTYPE_IPV4);
        DnsHeader dnsHeader = DnsHeader.query(0x1234, true, 1);
        DnsQuestion question = new DnsQuestion("www.example.com",
                DnsQuestion.QTYPE_A, DnsQuestion.QCLASS_IN);
        byte[] dns = concat(dnsHeader.encode(), question.encode());

        IpHeader ip = new IpHeader(4, 5, 20 + 8 + dns.length, 1, 64,
                IpHeader.PROTOCOL_UDP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("8.8.8.8"));
        UdpHeader udp = new UdpHeader(53000, 53, 8 + dns.length, 0); // 目的端口 53 = DNS

        byte[] frame = concat(eth.encode(), ip.encode(), udp.encode(), dns);
        PacketParser.ParsedPacket parsed = PacketParser.parse(frame);

        // 传输层：UDP 目的端口 53（DNS）
        assertTrue(parsed.isUdp());
        UdpHeader parsedUdp = (UdpHeader) parsed.transport();
        assertEquals(53, parsedUdp.destinationPort(), "DNS 查询目的端口 53");
        assertEquals(dns.length, parsedUdp.payloadLength());
        assertEquals(dns.length, parsed.payloadLength());
        // 负载就是 DNS 报文（UDP 首部之后）：12 字节头部 + 21 字节查询记录
        int udpOffset = EthernetFrame.HEADER_LENGTH + 20 + UdpHeader.HEADER_LENGTH;
        byte[] dnsPayload = java.util.Arrays.copyOfRange(frame, udpOffset, frame.length);
        DnsHeader parsedDnsHeader = DnsHeader.parse(dnsPayload, 0);
        assertEquals(0x1234, parsedDnsHeader.id());
        assertEquals(1, parsedDnsHeader.questionCount());
        DnsQuestion.ParsedQuestion parsedQuestion = DnsQuestion.parseAt(
                dnsPayload, DnsHeader.HEADER_LENGTH);
        assertEquals("www.example.com", parsedQuestion.question().name());
    }
}
