package com.study.concurrency.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.study.concurrency.tps.BatchProcessor;
import com.study.concurrency.tps.SimpleConnectionPool;
import com.study.concurrency.tps.SimpleConnectionPool.ExpensiveHandle;

/**
 * 压测对比主程序：把本模块的技巧放在同一台机器上量化对比——
 * 「多线程 vs 单线程」「批量 vs 逐条」「连接池 vs 每请求新建」。
 *
 * <p>学习方法：先看结论（谁能快多少倍），再回对应专题读实现细节；本机是虚拟机/容器时
 * 绝对数值会偏低，但「相对快慢」的结论基本稳定。
 *
 * <p>注意：压测是演示性质，没有做 JIT 预热/多次取中位数等严谨统计，学习「思路」而非「基准数值」。
 *
 * <p>运行：mvn compile exec:java -pl module-17-high-concurrency
 *        -Dexec.mainClass=com.study.concurrency.bench.BenchmarkDemo
 */
public final class BenchmarkDemo {

    private BenchmarkDemo() {
    }

    /** 对一个 int 数组求和（每次调用重新遍历，避免 JIT 缓存手感）。 */
    private static long sum(int[] data) {
        long s = 0;
        for (int v : data) {
            s += v;
        }
        return s;
    }

    /** 对数组 [from, to) 区间求和：并行任务直接分段遍历，避免复制数组干扰对比（包内测试直接调用）。 */
    static long sumRange(int[] data, int from, int to) {
        long s = 0;
        for (int i = from; i < to; i++) {
            s += data[i];
        }
        return s;
    }

    /** 对比 1：单线程 / 固定线程池 / 虚拟线程 并行求和。 */
    private static void threadModeBenchmark() throws Exception {
        System.out.println("【对比 1】并行求和 2000 万个数");
        int[] data = IntStream.range(0, 20_000_000).map(i -> i % 7).toArray();
        long expected = sum(data);

        long t0 = System.nanoTime();
        long r1 = sum(data);
        long t1 = System.nanoTime();
        // 固定线程池（4 线程，分 4 段并行）
        ExecutorService pool = Executors.newFixedThreadPool(4);
        long t2 = System.nanoTime();
        List<Future<Long>> futures = new ArrayList<>();
        int chunk = data.length / 4;
        for (int i = 0; i < 4; i++) {
            int from = i * chunk;
            int to = (i == 3) ? data.length : from + chunk;
            int finalFrom = from; // lambda 捕获需 effectively final
            int finalTo = to;
            futures.add(pool.submit(() -> sumRange(data, finalFrom, finalTo)));
        }
        long r2 = 0;
        for (Future<Long> f : futures) {
            r2 += f.get();
        }
        long t3 = System.nanoTime();
        pool.shutdownNow();
        // 虚拟线程（分段更多，虚拟线程便宜所以段数可以开粗）
        long t4 = System.nanoTime();
        long r3;
        try (ExecutorService vp = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Long>> vfutures = new ArrayList<>();
            int vchunk = data.length / 8;
            for (int i = 0; i < 8; i++) {
                int from = i * vchunk;
                int to = (i == 7) ? data.length : from + vchunk;
                int finalFrom = from;
                int finalTo = to;
                vfutures.add(vp.submit(() -> sumRange(data, finalFrom, finalTo)));
            }
            r3 = 0;
            for (Future<Long> f : vfutures) {
                r3 += f.get();
            }
        }
        long t5 = System.nanoTime();
        System.out.printf("    单线程: %.1f ms%n", (t1 - t0) / 1_000_000.0);
        System.out.printf("    4 线程池: %.1f ms%n", (t3 - t2) / 1_000_000.0);
        System.out.printf("    8 虚拟线程: %.1f ms%n", (t5 - t4) / 1_000_000.0);
        System.out.printf("    三组结果一致=%b%n", r1 == expected && r2 == expected && r3 == expected);
        System.out.println("    结论: 并行收益取决于 CPU 核数与任务量——核少/任务小时并行反而有调度开销，"
                + "线程数 ≈ CPU 核数即可，不是越多越快");
    }

    /** 对比 2：批量 vs 逐条（底层模拟固定开销）。 */
    private static void batchBenchmark() {
        System.out.println("【对比 2】批量写入 vs 逐条写入（模拟底库固定开销 1ms/次）");
        BatchProcessor.benchmarkDemo();
    }

    /** 对比 3：每请求新建连接 vs 连接池复用。 */
    private static void connectionPoolBenchmark() throws Exception {
        System.out.println("【对比 3】连接复用：20 个任务，池容量 3");
        SimpleConnectionPool.demo();
    }

    /** 运行全部对比。 */
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println(" module-17 压测对比（本机结果会有波动，看相对倍率）");
        System.out.println("========================================");
        threadModeBenchmark();
        System.out.println();
        batchBenchmark();
        System.out.println();
        connectionPoolBenchmark();
        System.out.println();
        System.out.println("========================================");
        System.out.println(" 压测结束");
        System.out.println("========================================");
    }
}