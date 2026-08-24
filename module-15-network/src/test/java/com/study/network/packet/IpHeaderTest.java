package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // ---- 分片三件套（标识 / 标志 / 片偏移） ----

    @Test
    @DisplayName("分片字段往返：DF 标志 + 片偏移 185（1480 字节 ÷ 8）编码解析一致")
    void fragmentationRoundTrip() {
        IpHeader original = new IpHeader(4, 5, 40, 0x1234,
                IpHeader.FLAG_DF, 185,
                64, IpHeader.PROTOCOL_TCP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("93.184.216.34"));

        IpHeader parsed = IpHeader.parse(original.encode());
        assertEquals(0x1234, parsed.identification());
        assertEquals(IpHeader.FLAG_DF, parsed.flags());
        assertEquals(185, parsed.fragmentOffset());
        assertEquals(185 * 8, parsed.fragmentOffset() * 8, "片偏移单位是 8 字节");
    }

    @Test
    @DisplayName("标志位字节布局：MF=0x20、DF=0x40、DF+MF=0x60 写入第 6 字节高 3 位")
    void flagBitLayout() {
        IpHeader mf = new IpHeader(4, 5, 40, 1, IpHeader.FLAG_MF, 0, 64, 17, 0, 0, 0);
        assertEquals(0x20, mf.encode()[6] & 0xFF, "MF 置 1 -> bit13");
        assertEquals(0x00, mf.encode()[7] & 0xFF);

        IpHeader df = new IpHeader(4, 5, 40, 1, IpHeader.FLAG_DF, 0, 64, 17, 0, 0, 0);
        assertEquals(0x40, df.encode()[6] & 0xFF, "DF 置 1 -> bit14");

        IpHeader both = new IpHeader(4, 5, 40, 1, IpHeader.FLAG_DF | IpHeader.FLAG_MF, 0,
                64, 17, 0, 0, 0);
        assertEquals(0x60, both.encode()[6] & 0xFF);
    }

    @Test
    @DisplayName("片偏移最大 8191（13 bit）：最大值往返一致，描述方法输出 DF/MF")
    void fragmentOffsetMax() {
        IpHeader original = new IpHeader(4, 5, 40, 1,
                IpHeader.FLAG_MF, 0x1FFF,
                64, 17, 0, 0, 0);
        IpHeader parsed = IpHeader.parse(original.encode());
        assertEquals(0x1FFF, parsed.fragmentOffset());
        assertEquals("MF", parsed.flagsDescription());

        IpHeader df = original.withFragmentation(IpHeader.FLAG_DF, 0);
        assertEquals("DF", df.flagsDescription());
        assertEquals(IpHeader.FLAG_DF, IpHeader.parse(df.encode()).flags());
    }

    @Test
    @DisplayName("分片重组：3000 字节数据按 MTU=1500 分 3 片（1480+1480+40），偏移 0/185/370")
    void reassembleFragments() {
        // 分片 1：数据 1480 字节，偏移 0，MF=1
        IpHeader f1 = new IpHeader(4, 5, 20 + 1480, 0xABCD, IpHeader.FLAG_MF, 0,
                64, IpHeader.PROTOCOL_TCP, 0, 0, 0);
        // 分片 2：数据 1480 字节，偏移 185（1480/8），MF=1
        IpHeader f2 = new IpHeader(4, 5, 20 + 1480, 0xABCD, IpHeader.FLAG_MF, 185,
                64, IpHeader.PROTOCOL_TCP, 0, 0, 0);
        // 分片 3：数据 40 字节，偏移 370（2960/8），MF=0（最后一片）
        IpHeader f3 = new IpHeader(4, 5, 20 + 40, 0xABCD, 0, 370,
                64, IpHeader.PROTOCOL_TCP, 0, 0, 0);

        // 乱序传入也能重组（内部按片偏移排序）
        assertEquals(3000, IpHeader.reassembledDataLength(List.of(f2, f3, f1)),
                "重组后的数据长度 = 1480+1480+40");
    }

    @Test
    @DisplayName("分片重组失败：标识不一致 / 偏移不连续（中间缺片）必须拒绝")
    void reassembleRejectsBadFragments() {
        IpHeader base = new IpHeader(4, 5, 20 + 1480, 0xABCD, IpHeader.FLAG_MF, 0,
                64, IpHeader.PROTOCOL_TCP, 0, 0, 0);
        // 标识不一致：属于不同数据报的分片
        IpHeader otherId = new IpHeader(4, 5, 20 + 1480, 0x9999, IpHeader.FLAG_MF, 185,
                64, IpHeader.PROTOCOL_TCP, 0, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> IpHeader.reassembledDataLength(List.of(base, otherId)));

        // 偏移不连续：从偏移 0 直接跳到 370（缺 185 那一片）
        IpHeader jumped = new IpHeader(4, 5, 20 + 40, 0xABCD, 0, 370,
                64, IpHeader.PROTOCOL_TCP, 0, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> IpHeader.reassembledDataLength(List.of(base, jumped)));

        // 空列表拒绝
        assertThrows(IllegalArgumentException.class,
                () -> IpHeader.reassembledDataLength(List.of()));
    }

    @Test
    @DisplayName("非法分片参数被拒绝：flags 超出 3 bit、片偏移超出 13 bit")
    void invalidFragmentationRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpHeader(4, 5, 40, 1, 8, 0, 64, 17, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IpHeader(4, 5, 40, 1, 0, 0x2000, 64, 17, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IpHeader(4, 5, 40, 1, 0, -1, 64, 17, 0, 0, 0));
    }
}
