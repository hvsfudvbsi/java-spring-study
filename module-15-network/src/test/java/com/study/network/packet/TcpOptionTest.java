package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP 选项测试：验证 MSS/Window Scale/SACK/时间戳 的构造、编码（NOP 对齐）与解析。
 */
class TcpOptionTest {

    @Test
    @DisplayName("MSS 编码：kind=2、len=4、值 05 B4（1460），正好 4 字节")
    void mssEncoding() {
        TcpOption mss = TcpOption.mss(1460);
        assertArrayEquals(new byte[]{0x02, 0x04, 0x05, (byte) 0xB4}, mss.encode(List.of(mss)));
        assertEquals(1460, mss.shortValue());
        assertEquals("MSS", mss.kindName());
    }

    @Test
    @DisplayName("MSS 往返：编码后按字节解析回 MSS=1460")
    void mssRoundTrip() {
        TcpOption mss = TcpOption.mss(1460);
        List<TcpOption> parsed = TcpOption.parse(mss.encode(List.of(mss)), 0, 4);
        assertEquals(1, parsed.size());
        assertEquals(TcpOption.KIND_MSS, parsed.get(0).kind());
        assertEquals(4, parsed.get(0).length());
        assertEquals(1460, parsed.get(0).shortValue());
    }

    @Test
    @DisplayName("Window Scale 编码：kind=3、len=3、值 7，NOP 填充到 4 字节")
    void windowScaleEncoding() {
        TcpOption ws = TcpOption.windowScale(7);
        assertArrayEquals(new byte[]{0x03, 0x03, 0x07, 0x01}, ws.encode(List.of(ws)),
                "3 字节选项 NOP 填充到 4 字节边界");
        assertEquals(7, ws.byteValue());
        assertEquals("Window Scale", ws.kindName());
    }

    @Test
    @DisplayName("SACK-Permitted：kind=4、len=2、无值，NOP 填充到 4 字节")
    void sackPermittedEncoding() {
        TcpOption sack = TcpOption.sackPermitted();
        assertArrayEquals(new byte[]{0x04, 0x02, 0x01, 0x01}, sack.encode(List.of(sack)),
                "2 字节选项 NOP 填充到 4 字节边界");
        assertEquals(TcpOption.KIND_SACK_PERMITTED, sack.kind());
        assertEquals(2, sack.length());
    }

    @Test
    @DisplayName("Timestamp 往返：TSval=1000、TSecr=2000 编码为 10 字节后解析回")
    void timestampRoundTrip() {
        TcpOption ts = TcpOption.timestamp(1000, 2000);
        assertEquals(10, ts.length());
        assertEquals("Timestamp", ts.kindName());
        List<TcpOption> parsed = TcpOption.parse(ts.encode(List.of(ts)), 0, 10);
        assertEquals(1, parsed.size());
        assertEquals(TcpOption.KIND_TIMESTAMP, parsed.get(0).kind());
        assertArrayEquals(ts.value(), parsed.get(0).value());
    }

    @Test
    @DisplayName("NOP 填充：NOP+MSS 共 5 字节，对齐到 8 字节，解析回来 2 个选项")
    void nopPaddingAlignment() {
        List<TcpOption> options = List.of(TcpOption.noOp(), TcpOption.mss(1460));
        assertEquals(8, TcpOption.totalLength(options), "1+4=5 字节对齐到 8");

        byte[] encoded = TcpOption.encode(options);
        assertEquals(8, encoded.length);
        assertEquals(0x01, encoded[0] & 0xFF, "第一个是 NOP");
        assertEquals(0x02, encoded[1] & 0xFF, "第二个是 MSS kind");
        assertEquals(0x01, encoded[5] & 0xFF, "末尾 3 字节是 NOP 填充");

        List<TcpOption> parsed = TcpOption.parse(encoded, 0, encoded.length);
        assertEquals(1, parsed.size(), "NOP 是填充字节，解析时被跳过");
        assertEquals(TcpOption.KIND_MSS, parsed.get(0).kind());
        assertEquals(1460, parsed.get(0).shortValue());
    }

    @Test
    @DisplayName("EOL 提前结束解析：NOP + MSS + EOL + 垃圾字节，解析停在 EOL")
    void eolStopsParsing() {
        byte[] block = new byte[]{0x01, 0x02, 0x04, 0x05, (byte) 0xB4, 0x00, 0x7F, 0x7F};
        List<TcpOption> parsed = TcpOption.parse(block, 0, block.length);
        assertEquals(1, parsed.size(), "NOP 被跳过、EOL 之后的内容不再解析");
        assertEquals(TcpOption.KIND_MSS, parsed.get(0).kind());
    }

    @Test
    @DisplayName("多选项组合：MSS+WS+SACK-Permitted 一起编码解析")
    void combinedOptions() {
        List<TcpOption> options = List.of(
                TcpOption.mss(1460), TcpOption.windowScale(7), TcpOption.sackPermitted());
        assertEquals(12, TcpOption.totalLength(options), "4+3+2=9 字节对齐到 12");

        byte[] encoded = TcpOption.encode(options);
        assertEquals(12, encoded.length);
        List<TcpOption> parsed = TcpOption.parse(encoded, 0, encoded.length);
        assertEquals(3, parsed.size());
        assertEquals(TcpOption.KIND_MSS, parsed.get(0).kind());
        assertEquals(TcpOption.KIND_WINDOW_SCALE, parsed.get(1).kind());
        assertEquals(7, parsed.get(1).byteValue());
        assertEquals(TcpOption.KIND_SACK_PERMITTED, parsed.get(2).kind());
    }

    @Test
    @DisplayName("非法构造被拒绝：MSS 值长度与 Length 不匹配、EOL/NOP 带值")
    void invalidConstructionRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TcpOption(TcpOption.KIND_MSS, 4, new byte[3]), "value 必须 = length - 2");
        assertThrows(IllegalArgumentException.class,
                () -> new TcpOption(TcpOption.KIND_NOP, 2, new byte[0]), "NOP 不应有 Length");
        assertThrows(IllegalArgumentException.class,
                () -> new TcpOption(256, 4, new byte[2]), "Kind 超出 1 字节");
    }

    @Test
    @DisplayName("非法报文被拒绝：选项缺少 Length 字节、Length 越界")
    void invalidBlockRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TcpOption.parse(new byte[]{0x02}, 0, 1), "MSS 后缺少 Length");
        assertThrows(IllegalArgumentException.class,
                () -> TcpOption.parse(new byte[]{0x02, 0x04, 0x05}, 0, 3), "Length 声明 4 但越界");
    }

    @Test
    @DisplayName("未知 Kind 透传：0x1E（TFO）按结构解析，不报错")
    void unknownKindParsed() {
        byte[] block = new byte[]{0x1E, 0x04, 0x00, 0x00};
        List<TcpOption> parsed = TcpOption.parse(block, 0, block.length);
        assertEquals(1, parsed.size());
        assertEquals(0x1E, parsed.get(0).kind());
        assertTrue(parsed.get(0).kindName().startsWith("未知"));
    }

    @Test
    @DisplayName("SACK 单块往返：乱序区间 [1000, 2000) 编码为 10 字节后解析回")
    void sackSingleBlockRoundTrip() {
        TcpOption sack = TcpOption.sack(new SackBlock(1000, 2000));
        assertEquals(TcpOption.KIND_SACK, sack.kind());
        assertEquals(10, sack.length(), "2(头) + 8(一块) = 10 字节");

        List<TcpOption> parsed = TcpOption.parse(sack.encode(List.of(sack)), 0, 10);
        assertEquals(1, parsed.size());
        List<SackBlock> blocks = parsed.get(0).sackBlocks();
        assertEquals(1, blocks.size());
        assertEquals(1000, blocks.get(0).leftEdge());
        assertEquals(2000, blocks.get(0).rightEdge());
        assertEquals(1000, blocks.get(0).size());
        assertEquals("SACK", parsed.get(0).kindName());
    }

    @Test
    @DisplayName("SACK 块字节布局：每块 8 字节大端，左边界 + 右边界，NOP 填充到 12")
    void sackBlockByteLayout() {
        TcpOption sack = TcpOption.sack(new SackBlock(0x000003E8L, 0x000007D0L)); // 1000, 2000
        byte[] encoded = sack.encode(List.of(sack));
        assertArrayEquals(new byte[]{0x05, 0x0A, 0x00, 0x00, 0x03, (byte) 0xE8, 0x00, 0x00, 0x07, (byte) 0xD0,
                        0x01, 0x01},
                encoded, "kind=5, len=10, 左 1000(03E8) 右 2000(07D0)，10 字节对齐到 12（末尾 2 个 NOP）");
        assertEquals("SACK", sack.kindName());
    }

    @Test
    @DisplayName("SACK 多块往返：3 个乱序区间，第一块是最近收到的段")
    void sackMultipleBlocksRoundTrip() {
        // 场景：发送序号 1000~4000，接收方只收到 1000~2000、2500~2600、3000~3500
        // （中间 2000~2500、2600~3000 丢失）；第一个块是含最大序号的最近接收段
        List<SackBlock> received = List.of(
                new SackBlock(3000, 3500),  // 最近（最大序号）
                new SackBlock(2500, 2600),
                new SackBlock(1000, 2000));
        TcpOption sack = TcpOption.sack(received.toArray(new SackBlock[0]));
        assertEquals(26, sack.length(), "2 + 3×8 = 26 字节");

        List<TcpOption> parsed = TcpOption.parse(sack.encode(List.of(sack)), 0, 26);
        assertEquals(1, parsed.size());
        List<SackBlock> blocks = parsed.get(0).sackBlocks();
        assertEquals(3, blocks.size());
        assertEquals(3000, blocks.get(0).leftEdge());
        assertEquals(3500, blocks.get(0).rightEdge());
        assertEquals(2500, blocks.get(1).leftEdge());
        assertEquals(2000, blocks.get(2).rightEdge());
    }

    @Test
    @DisplayName("序号回绕：跨 2^32 边界的块 [0xFFFFFFF0, 0x10) 长度与包含判断正确")
    void sackBlockWraparound() {
        SackBlock wrap = new SackBlock(0xFFFF_FFF0L, 0x10);
        assertEquals(32, wrap.size(), "(0x10 - 0xFFFFFFF0) 按模 2^32 = 32");
        assertTrue(wrap.contains(0xFFFF_FFF0L), "左边界在区间内");
        assertTrue(wrap.contains(0L), "回绕后的序号 0 在区间内");
        assertTrue(wrap.contains(0x0FL), "0x0F 是最后一个字节");
        org.junit.jupiter.api.Assertions.assertFalse(wrap.contains(0x10L), "右边界是开区间，不含");

        TcpOption sack = TcpOption.sack(wrap);
        List<TcpOption> parsed = TcpOption.parse(sack.encode(List.of(sack)), 0, sack.length());
        assertEquals(new SackBlock(0xFFFF_FFF0L, 0x10), parsed.get(0).sackBlocks().get(0));
    }

    @Test
    @DisplayName("SACK 与 MSS 组合：4+34=38 字节对齐到 40（选项区域上限）")
    void sackCombinedWithMss() {
        List<TcpOption> options = List.of(
                TcpOption.mss(1460),
                TcpOption.sack(new SackBlock(3000, 3500), new SackBlock(1000, 2000),
                        new SackBlock(5000, 6000), new SackBlock(7000, 7500)));
        assertEquals(40, TcpOption.totalLength(options), "4 + (2+32) = 38 对齐到 40");

        byte[] encoded = TcpOption.encode(options);
        assertEquals(40, encoded.length);
        List<TcpOption> parsed = TcpOption.parse(encoded, 0, encoded.length);
        assertEquals(2, parsed.size());
        assertEquals(1460, parsed.get(0).shortValue());
        assertEquals(4, parsed.get(1).sackBlocks().size());
    }

    @Test
    @DisplayName("非法 SACK 构造被拒绝：0 块、5 块、空块（左右相等）")
    void invalidSackRejected() {
        assertThrows(IllegalArgumentException.class, () -> TcpOption.sack(), "至少要一个块");
        assertThrows(IllegalArgumentException.class,
                () -> TcpOption.sack(new SackBlock(1, 2), new SackBlock(3, 4), new SackBlock(5, 6),
                        new SackBlock(7, 8), new SackBlock(9, 10)), "最多 4 个块");
        assertThrows(IllegalArgumentException.class, () -> new SackBlock(100, 100), "空块");
        assertThrows(IllegalArgumentException.class, () -> new SackBlock(-1, 100), "负数越界");
        assertThrows(IllegalArgumentException.class,
                () -> new SackBlock(0, 0x1_0000_0000L), "超出 32 位");
    }

    @Test
    @DisplayName("非法 SACK 报文被拒绝：Value 长度不是 8 的倍数、非 SACK 选项调用解析")
    void invalidSackBlockRejected() {
        // kind=5, len=6, value 4 字节——不是 8 的倍数，解析块时拒绝
        TcpOption malformed = new TcpOption(TcpOption.KIND_SACK, 6, new byte[4]);
        assertThrows(IllegalArgumentException.class, malformed::sackBlocks);

        TcpOption mss = TcpOption.mss(1460);
        assertThrows(IllegalStateException.class, mss::sackBlocks, "非 SACK 选项不能解析块");
    }
}
