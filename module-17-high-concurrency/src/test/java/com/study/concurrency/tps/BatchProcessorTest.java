package com.study.concurrency.tps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchProcessorTest {

    private static List<String> items(int n) {
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add("item-" + i);
        }
        return list;
    }

    @Test
    @DisplayName("正确性: 逐条与批量写入行数均等于条目数")
    void bothWriteAllRows() {
        List<String> items = items(37);
        assertEquals(37, BatchProcessor.writeOneByOne(items));
        assertEquals(37, BatchProcessor.writeBatched(items, 10));
    }

    @Test
    @DisplayName("批量快于逐条: 固定开销被摊薄（100 条、批 50，耗时差 10 倍量级）")
    void batchedIsFaster() {
        List<String> items = items(100);
        long t0 = System.nanoTime();
        BatchProcessor.writeOneByOne(items);
        long t1 = System.nanoTime();
        BatchProcessor.writeBatched(items, 50);
        long t2 = System.nanoTime();

        double singleMs = (t1 - t0) / 1_000_000.0;
        double batchMs = (t2 - t1) / 1_000_000.0;
        assertTrue(batchMs < singleMs / 3,
                "批量应显著快于逐条: 逐条=" + singleMs + "ms 批量=" + batchMs + "ms");
    }

    @Test
    @DisplayName("批大小大于总数: 一次调用写完，不抛异常")
    void batchSizeLargerThanList() {
        assertEquals(5, BatchProcessor.writeBatched(items(5), 100));
    }

    @Test
    @DisplayName("非法批大小: 小于等于 0 抛 IllegalArgumentException")
    void invalidBatchSizeRejected() {
        assertThrows(IllegalArgumentException.class, () -> BatchProcessor.writeBatched(items(5), 0));
        assertThrows(IllegalArgumentException.class, () -> BatchProcessor.writeBatched(items(5), -1));
    }

    @Test
    @DisplayName("空列表: 逐条与批量都返回 0")
    void emptyList() {
        assertEquals(0, BatchProcessor.writeOneByOne(List.of()));
        assertEquals(0, BatchProcessor.writeBatched(List.of(), 10));
    }
}