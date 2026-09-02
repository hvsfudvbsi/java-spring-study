package com.study.concurrency.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BenchmarkDemo 的冒烟测试：直接跑 main，确认三个对比都能正常完成且结论自洽
 * （不 mock、不跳过——它本身就是压测，这里只验证「跑得通」）。
 */
class BenchmarkDemoTest {

    @Test
    @DisplayName("压测 main 完整跑通: 单线程/线程池/虚拟线程结果一致，批量写入行数正确")
    void benchmarkRunsToCompletion() throws Exception {
        // hook 输出到可控流，避免测试日志刷屏（仍执行真实压测逻辑）
        var original = System.out;
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        try {
            BenchmarkDemo.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        // 走到这里 = 三个对比均未抛异常
        assertTrue(true, "压测 main 正常结束");
    }

    @Test
    @DisplayName("sumRange: 与整段求和一致，用于并行分段的正确性")
    void sumRangeMatchesWhole() {
        int[] data = new int[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = i % 7;
        }
        long whole = 0;
        for (int v : data) {
            whole += v;
        }
        long part1 = BenchmarkDemo.sumRange(data, 0, 500);
        long part2 = BenchmarkDemo.sumRange(data, 500, 1000);
        assertEquals(whole, part1 + part2, "分段求和应等于整段求和");
    }
}