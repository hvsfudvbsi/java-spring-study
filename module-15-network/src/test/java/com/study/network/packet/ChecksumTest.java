package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验和测试：验证 IP 反码和、TCP/UDP 伪首部校验和（含 RFC 1071 官方向量）与整体验证。
 */
class ChecksumTest {

    private static final int SRC_IP = IpHeader.parseIp("192.168.1.10");
    private static final int DST_IP = IpHeader.parseIp("93.184.216.34");

    // ---- IP 首部校验和 ----

    @Test
    @DisplayName("RFC 1071 官方向量：IP 首部校验和为 0xB861")
    void ipHeaderChecksumRfc1071Vector() {
        // RFC 1071 文档示例：校验和字段已置 0
        byte[] header = hex("450000730000400040110000c0a80001c0a800c7");
        assertEquals(0xB861, Checksums.ipHeaderChecksum(header));
    }

    @Test
    @DisplayName("反码和折叠：全 1 数据反码和为 0xFFFF，校验和为 0x0000")
    void allOnesSum() {
        byte[] data = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(0xFFFF, Checksums.onesComplementSum(data), "0x1FFFE 折叠为 0xFFFF");
        assertEquals(0x0000, Checksums.complement(Checksums.onesComplementSum(data)));
    }

    @Test
    @DisplayName("奇数长度按 0 补齐：单字节 0x01 按 0x0100 参与求和")
    void oddLengthPaddedWithZero() {
        assertEquals(0x0100, Checksums.onesComplementSum(new byte[]{0x01}));
        assertEquals(0xFEFF, Checksums.complement(Checksums.onesComplementSum(new byte[]{0x01})));
    }

    // ---- TCP 校验和 ----

    @Test
    @DisplayName("TCP 校验和向量：SYN 报文（空负载）校验和为 0x83E4")
    void tcpChecksumSynVector() {
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        assertEquals(0x83E4, syn.computeChecksum(SRC_IP, DST_IP, new byte[0]));
    }

    @Test
    @DisplayName("TCP 校验和覆盖数据：带负载与空负载的校验和不同")
    void tcpChecksumIncludesPayload() {
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        byte[] payload = "GET / HTTP/1.1".getBytes(StandardCharsets.UTF_8);
        assertFalse(syn.computeChecksum(SRC_IP, DST_IP, payload)
                == syn.computeChecksum(SRC_IP, DST_IP, new byte[0]));
    }

    @Test
    @DisplayName("TCP 校验和覆盖伪首部：换源 IP 后校验和改变")
    void tcpChecksumIncludesPseudoHeader() {
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        int other = IpHeader.parseIp("10.0.0.1");
        assertFalse(syn.computeChecksum(SRC_IP, DST_IP, new byte[0])
                == syn.computeChecksum(other, DST_IP, new byte[0]));
    }

    @Test
    @DisplayName("TCP 校验和整体验证：含校验和的报文段反码和为 0xFFFF，篡改一字节后失败")
    void tcpChecksumVerify() {
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        TcpHeader withChecksum = syn.withValidChecksum(SRC_IP, DST_IP, payload);
        byte[] segment = withChecksum.segment(payload);

        // 整体反码和应为全 1
        assertTrue(Checksums.verifyTransport(SRC_IP, DST_IP, IpHeader.PROTOCOL_TCP,
                withChecksum.headerLength() + payload.length, segment));

        // 篡改负载中的一个字节，验证必须失败
        segment[segment.length - 1] ^= 0x01;
        assertFalse(Checksums.verifyTransport(SRC_IP, DST_IP, IpHeader.PROTOCOL_TCP,
                withChecksum.headerLength() + payload.length, segment));
    }

    // ---- UDP 校验和 ----

    @Test
    @DisplayName("UDP 校验和向量：数据 test、长度 12 的校验和为 0x5131")
    void udpChecksumVector() {
        UdpHeader header = new UdpHeader(53000, 53, 8 + 4, 0);
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);
        assertEquals(0x5131, header.computeChecksum(SRC_IP, DST_IP, payload));
    }

    @Test
    @DisplayName("UDP 校验和整体验证：含校验和的数据报反码和为 0xFFFF，篡改后失败")
    void udpChecksumVerify() {
        UdpHeader header = new UdpHeader(53000, 53, 8 + 4, 0);
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);
        UdpHeader withChecksum = header.withValidChecksum(SRC_IP, DST_IP, payload);
        byte[] datagram = withChecksum.datagram(payload);

        assertTrue(Checksums.verifyTransport(SRC_IP, DST_IP, IpHeader.PROTOCOL_UDP,
                withChecksum.length(), datagram));

        datagram[datagram.length - 1] ^= 0x01; // 篡改最后一个数据字节
        assertFalse(Checksums.verifyTransport(SRC_IP, DST_IP, IpHeader.PROTOCOL_UDP,
                withChecksum.length(), datagram));
    }

    @Test
    @DisplayName("UDP 校验和覆盖伪首部：换目的 IP 后校验和改变（UDP 默认不填校验和也能发）")
    void udpChecksumIncludesPseudoHeader() {
        UdpHeader header = new UdpHeader(53000, 53, 8 + 4, 0);
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);
        int other = IpHeader.parseIp("8.8.8.8");
        assertFalse(header.computeChecksum(SRC_IP, DST_IP, payload)
                == header.computeChecksum(SRC_IP, other, payload));

        // IPv4 下 UDP 校验和可选：checksum=0 表示未计算，编码/解析不受影响
        assertEquals(0, header.checksum());
        assertEquals(0, UdpHeader.parse(header.encode()).checksum());
    }

    @Test
    @DisplayName("UDP 校验和覆盖数据：负载参与计算，与空负载校验和不同")
    void udpChecksumIncludesPayload() {
        UdpHeader header = new UdpHeader(53000, 53, 8 + 4, 0);
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);
        assertFalse(header.computeChecksum(SRC_IP, DST_IP, payload)
                == header.computeChecksum(SRC_IP, DST_IP, new byte[0]));
    }

    /** 16 进制字符串转字节数组。 */
    private static byte[] hex(String s) {
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
