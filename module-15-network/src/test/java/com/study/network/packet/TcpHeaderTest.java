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

    // ---- 补全的标志位：URG / CWR / ECE ----

    @Test
    @DisplayName("URG 标志：0x20 写入第 13 字节，解析回来一致")
    void urgFlag() {
        TcpHeader urg = new TcpHeader(1, 2, 0, 0,
                5, false, false, false, false, false, true, false, false, 0, 0, 100);
        assertEquals(0x20, urg.encode()[13] & 0xFF, "URG = 0x20");
        TcpHeader parsed = TcpHeader.parse(urg.encode());
        assertTrue(parsed.urg(), "URG 标志应为 true");
        assertEquals(100, parsed.urgentPointer(), "URG 与紧急指针配合");
    }

    @Test
    @DisplayName("CWR/ECE 标志：0x80 / 0x40 写入第 13 字节，可组合")
    void cwrEceFlags() {
        TcpHeader cwr = new TcpHeader(1, 2, 0, 0,
                5, false, false, false, false, false, false, true, false, 0, 0, 0);
        assertEquals(0x80, cwr.encode()[13] & 0xFF, "CWR = 0x80");
        assertTrue(TcpHeader.parse(cwr.encode()).cwr());

        TcpHeader ece = new TcpHeader(1, 2, 0, 0,
                5, false, false, false, false, false, false, false, true, 0, 0, 0);
        assertEquals(0x40, ece.encode()[13] & 0xFF, "ECE = 0x40");
        assertTrue(TcpHeader.parse(ece.encode()).ece());

        // 全部 8 个标志同时置位：0x80|0x40|0x20|0x10|0x08|0x04|0x02|0x01 = 0xFF
        TcpHeader all = new TcpHeader(1, 2, 0, 0,
                5, true, true, true, true, true, true, true, true, 0, 0, 0);
        assertEquals(0xFF, all.encode()[13] & 0xFF);
        TcpHeader parsedAll = TcpHeader.parse(all.encode());
        assertTrue(parsedAll.cwr() && parsedAll.ece() && parsedAll.urg()
                && parsedAll.ack() && parsedAll.psh() && parsedAll.rst()
                && parsedAll.syn() && parsedAll.fin());
    }

    // ---- TCP 选项 ----

    @Test
    @DisplayName("带 MSS 选项的 SYN：数据偏移自动变为 6（24 字节），往返解析出选项")
    void synWithMssOption() {
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        TcpHeader withMss = syn.withOptions(java.util.List.of(TcpOption.mss(1460)));

        assertEquals(6, withMss.dataOffset(), "4 字节选项 -> 数据偏移 5+1=6");
        assertEquals(24, withMss.headerLength());
        assertEquals(24, withMss.encode().length, "首部编码为 24 字节");
        assertTrue(withMss.syn(), "选项拷贝不改变标志位");

        TcpHeader parsed = TcpHeader.parse(withMss.encode());
        assertEquals(6, parsed.dataOffset());
        assertEquals(1, parsed.options().size());
        assertEquals(TcpOption.KIND_MSS, parsed.options().get(0).kind());
        assertEquals(1460, parsed.options().get(0).shortValue());
        // 无选项的原始 SYN 仍保持 20 字节
        assertEquals(20, syn.encode().length);
    }

    @Test
    @DisplayName("多选项：MSS+WS+SACK-Permitted 编码解析往返，dataOffset 增长正确")
    void multipleOptions() {
        TcpHeader syn = new TcpHeader(1, 2, 0, 0,
                5, false, true, false, false, false, 0, 0, 0);
        TcpHeader withOptions = syn.withOptions(java.util.List.of(
                TcpOption.mss(1460), TcpOption.windowScale(7), TcpOption.sackPermitted()));

        assertEquals(5 + 3, withOptions.dataOffset(), "12 字节选项 -> 数据偏移 5+3=8");
        assertEquals(32, withOptions.encode().length);

        TcpHeader parsed = TcpHeader.parse(withOptions.encode());
        assertEquals(3, parsed.options().size());
        assertEquals(1460, parsed.options().get(0).shortValue());
        assertEquals(7, parsed.options().get(1).byteValue());
        assertEquals(TcpOption.KIND_SACK_PERMITTED, parsed.options().get(2).kind());
    }

    @Test
    @DisplayName("选项超出 4 bit 数据偏移上限（>15）被拒绝")
    void tooManyOptionsRejected() {
        TcpHeader syn = new TcpHeader(1, 2, 0, 0,
                5, false, true, false, false, false, 0, 0, 0);
        // 10 个 MSS = 40 字节选项 -> 数据偏移 5+10=15 恰好；11 个 = 44 字节 -> 16 越界
        java.util.List<TcpOption> max = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> TcpOption.mss(1460)).toList();
        assertEquals(15, syn.withOptions(max).dataOffset());
        java.util.List<TcpOption> overflow = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> TcpOption.mss(1460)).toList();
        assertThrows(IllegalArgumentException.class, () -> syn.withOptions(overflow));
    }

    @Test
    @DisplayName("带选项的首部校验和：MSS 选项参与计算，整体验证通过")
    void checksumWithOptions() {
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, 65535, 0, 0);
        TcpHeader withMss = syn.withOptions(java.util.List.of(TcpOption.mss(1460)));
        byte[] payload = "GET / HTTP/1.1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        TcpHeader withChecksum = withMss.withValidChecksum(
                com.study.network.packet.IpHeader.parseIp("192.168.1.10"),
                com.study.network.packet.IpHeader.parseIp("93.184.216.34"), payload);
        byte[] segment = withChecksum.segment(payload);

        assertTrue(com.study.network.packet.Checksums.verifyTransport(
                com.study.network.packet.IpHeader.parseIp("192.168.1.10"),
                com.study.network.packet.IpHeader.parseIp("93.184.216.34"),
                com.study.network.packet.IpHeader.PROTOCOL_TCP,
                withChecksum.headerLength() + payload.length, segment),
                "选项参与校验和，整体反码和应为 0xFFFF");
    }
}
