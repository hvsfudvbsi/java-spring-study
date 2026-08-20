package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IPv4 首部测试：版本/IHL 位字段、点分十进制地址转换、协议号。
 */
class IpHeaderTest {

    @Test
    @DisplayName("IPv4 首部往返：版本 4、IHL 5、协议 TCP")
    void roundTrip() {
        IpHeader original = new IpHeader(4, 5, 20 + 20, 1,
                64, IpHeader.PROTOCOL_TCP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("93.184.216.34"));

        byte[] bytes = original.encode();
        assertEquals(20, bytes.length, "无选项 IPv4 首部固定 20 字节");

        IpHeader parsed = IpHeader.parse(bytes);
        assertEquals(4, parsed.version());
        assertEquals(5, parsed.ihl());
        assertEquals(20, parsed.headerLength(), "首部长度 = IHL * 4");
        assertEquals(40, parsed.totalLength());
        assertEquals(20, parsed.payloadLength(), "负载 = 总长度 - 首部");
        assertEquals(64, parsed.ttl());
        assertEquals(IpHeader.PROTOCOL_TCP, parsed.protocol());
        assertEquals("192.168.1.10", IpHeader.toIpString(parsed.sourceIp()));
        assertEquals("93.184.216.34", IpHeader.toIpString(parsed.destinationIp()));
    }

    @Test
    @DisplayName("版本与 IHL 挤在同一字节：高 4 位版本、低 4 位 IHL")
    void versionAndIhlShareByte() {
        IpHeader ip = new IpHeader(4, 5, 40, 1, 64, IpHeader.PROTOCOL_UDP, 0, 0, 0);
        byte[] bytes = ip.encode();
        assertEquals(0x45, bytes[0] & 0xFF, "0x45 = 版本4 | IHL5");

        IpHeader parsed = IpHeader.parse(bytes);
        assertEquals(4, parsed.version());
        assertEquals(5, parsed.ihl());
    }

    @Test
    @DisplayName("点分十进制与 32 位整数互转")
    void ipStringConversion() {
        int ip = IpHeader.parseIp("255.255.255.255");
        assertEquals("255.255.255.255", IpHeader.toIpString(ip));

        assertEquals("0.0.0.0", IpHeader.toIpString(0));
        assertThrows(IllegalArgumentException.class, () -> IpHeader.parseIp("999.1.1.1"));
        assertThrows(IllegalArgumentException.class, () -> IpHeader.parseIp("1.2.3"));
    }

    @Test
    @DisplayName("字节数不足 20 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> IpHeader.parse(new byte[19]));
    }

    @Test
    @DisplayName("带偏移解析：从完整报文中间位置提取 IP 首部")
    void parseWithOffset() {
        IpHeader original = new IpHeader(4, 5, 40, 1, 64, IpHeader.PROTOCOL_TCP, 0,
                IpHeader.parseIp("10.0.0.1"), IpHeader.parseIp("10.0.0.2"));
        byte[] padded = new byte[14 + 20]; // 模拟以太网帧头 14 字节 + IP 20 字节
        System.arraycopy(original.encode(), 0, padded, 14, 20);

        IpHeader parsed = IpHeader.parse(padded, 14);
        assertEquals("10.0.0.1", IpHeader.toIpString(parsed.sourceIp()));
        assertEquals("10.0.0.2", IpHeader.toIpString(parsed.destinationIp()));
        assertEquals(IpHeader.PROTOCOL_TCP, parsed.protocol());
    }
}
