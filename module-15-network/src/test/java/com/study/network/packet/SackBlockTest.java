package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SackBlock 测试：区间语义（size/contains）与丢包区间推断（gaps）。
 */
class SackBlockTest {

    @Test
    @DisplayName("基础区间：右开区间 [1000, 2000) 长度 1000，含左边界不含右边界")
    void basicIntervalSemantics() {
        SackBlock block = new SackBlock(1000, 2000);
        assertEquals(1000, block.size());
        assertTrue(block.contains(1000), "左边界（闭）在区间内");
        assertTrue(block.contains(1999), "最后一个字节在区间内");
        assertFalse(block.contains(2000), "右边界（开）不在区间内");
        assertFalse(block.contains(999), "左边界之前不在区间内");
    }

    @Test
    @DisplayName("回绕区间：跨 2^32 边界的块 [0xFFFFFFF0, 0x10) 长度与包含判断正确")
    void wraparoundSemantics() {
        SackBlock wrap = new SackBlock(0xFFFF_FFF0L, 0x10);
        assertEquals(32, wrap.size(), "(0x10 - 0xFFFFFFF0) 按模 2^32 = 32");
        assertTrue(wrap.contains(0xFFFF_FFF0L));
        assertTrue(wrap.contains(0L));
        assertTrue(wrap.contains(0x0FL));
        assertFalse(wrap.contains(0x10L));
        assertFalse(wrap.contains(0xFFFF_FFEFL), "左边界之前不在区间内");
    }

    @Test
    @DisplayName("丢包推断：demo 场景，发送 [1000,4000) 只收到两端 → 空隙 [2000,3000)")
    void gapsMiddleHole() {
        // 块乱序传入：先 [3000,4000) 后 [1000,2000)，排序后正确
        List<SackBlock> received = List.of(new SackBlock(3000, 4000), new SackBlock(1000, 2000));
        assertEquals(List.of(new SackBlock(2000, 3000)), SackBlock.gaps(1000, 4000, received));
    }

    @Test
    @DisplayName("丢包推断：多个空隙，块乱序传入也正确（先排序再求补集）")
    void gapsMultipleHoles() {
        // 发送 0~1000，只收到 [100,200) [400,500) [700,800)，其余全丢
        List<SackBlock> received = List.of(new SackBlock(700, 800), new SackBlock(100, 200),
                new SackBlock(400, 500));
        assertEquals(List.of(new SackBlock(0, 100), new SackBlock(200, 400),
                new SackBlock(500, 700), new SackBlock(800, 1000)),
                SackBlock.gaps(0, 1000, received));
    }

    @Test
    @DisplayName("丢包推断：一个都没收到 → 整个发送范围都要重传")
    void gapsNothingReceived() {
        assertEquals(List.of(new SackBlock(1000, 4000)), SackBlock.gaps(1000, 4000, List.of()));
    }

    @Test
    @DisplayName("丢包推断：块完全覆盖发送范围 → 无空隙")
    void gapsFullyCovered() {
        assertEquals(List.of(), SackBlock.gaps(0, 1000, List.of(new SackBlock(0, 1000))));
    }

    @Test
    @DisplayName("丢包推断：重叠/相邻块合并，不产生虚假空隙")
    void gapsOverlappingBlocksMerged() {
        // 相邻 [0,100)+[100,200) 无缝；重叠 [150,300)+[250,400) 合并为 [150,400)
        List<SackBlock> received = List.of(
                new SackBlock(0, 100), new SackBlock(100, 200),
                new SackBlock(150, 300), new SackBlock(250, 400));
        assertEquals(List.of(new SackBlock(400, 1000)), SackBlock.gaps(0, 1000, received));
    }

    @Test
    @DisplayName("丢包推断：块在发送范围之外被忽略（对当前窗口无影响）")
    void gapsBlockOutsideRangeIgnored() {
        // 发送 [1000,4000)；块在范围外（[500,800) 是之前窗口、[5000,6000) 是更远的段）
        List<SackBlock> received = List.of(new SackBlock(500, 800), new SackBlock(5000, 6000));
        assertEquals(List.of(new SackBlock(1000, 4000)), SackBlock.gaps(1000, 4000, received));
    }

    @Test
    @DisplayName("丢包推断：发送范围跨序号回绕点，正确求补")
    void gapsWraparoundRange() {
        // 发送 0xFFFFFFF0 ~ 0x30（跨回绕，共 64 字节），只收到 [0xFFFFFFF0, 0x10)
        List<SackBlock> received = List.of(new SackBlock(0xFFFF_FFF0L, 0x10));
        assertEquals(List.of(new SackBlock(0x10, 0x30)),
                SackBlock.gaps(0xFFFF_FFF0L, 0x30, received));
    }

    @Test
    @DisplayName("丢包推断：空发送范围 → 无空隙；非法边界拒绝")
    void gapsEdgeCases() {
        assertTrue(SackBlock.gaps(1000, 1000, List.of(new SackBlock(1000, 2000))).isEmpty(),
                "发送范围为空时没有可推断的丢包");
        assertThrows(IllegalArgumentException.class, () -> SackBlock.gaps(-1, 100, List.of()),
                "负数越界");
        assertThrows(IllegalArgumentException.class,
                () -> SackBlock.gaps(0, 0x1_0000_0000L, List.of()), "超出 32 位");
    }
}
