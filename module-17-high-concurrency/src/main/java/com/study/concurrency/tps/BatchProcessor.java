package com.study.concurrency.tps;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 批量处理演示：把「逐条调用」换成「攒一批再调用」，摊薄每次调用的固定开销（网络 RTT、事务提交、连接建立）。
 *
 * <p>现实场景：JDBC 的 addBatch/executeBatch、Redis pipeline、Kafka 批量发送、ClickHouse 批量插入，
 * 核心收益都是「固定开销只付一次」。本类用「平均每条固定开销 + 每条可变开销」模拟底层存储，
 * 对比逐条 vs 批量两种写法的耗时与吞吐。
 *
 * <p>学习目标：理解批量为什么快（摊薄固定开销）、批量大小的权衡（太大单次耗时长、内存占用高；
 * 太小收益不明显），以及批量失败时的局部性问题（一批失败会连坐整批）。
 */
public final class BatchProcessor {

    private static final long FIXED_OVERHEAD_NANOS = 1_000_000L; // 每次"调用"的固定开销：1ms
    private static final long PER_ITEM_NANOS = 100_000L;          // 每条数据的可变开销：0.1ms

    private BatchProcessor() {
    }

    /**
     * 模拟一次底层存储调用：耗时 = 固定开销 + 数据条数 × 单条开销，返回「成功写入的行数」。
     *
     * <p>用函数引用把「存储调用」抽象出来，便于测试注入真实的耗时模型。
     */
    public static final Function<List<String>, Long> BATCH_SINK = batch -> {
        // 模拟真实 I/O：小睡一下再返回写入行数
        busySleep(FIXED_OVERHEAD_NANOS + batch.size() * PER_ITEM_NANOS);
        return (long) batch.size();
    };

    /**
     * 逐条写入：每条数据都发起一次底层调用（固定开销每次都要付）。
     *
     * @return 写入的总行数
     */
    public static long writeOneByOne(List<String> items) {
        long total = 0;
        for (String item : items) {
            total += BATCH_SINK.apply(List.of(item));
        }
        return total;
    }

    /**
     * 批量写入：把数据按 batchSize 切块，每块只发起一次底层调用。
     *
     * <p>为什么分块而不是一次性全写：一次调用行数过多会单次耗时过长、内存占用高、
     * 失败时连坐范围大；分块能在「摊薄开销」和「单次大小可控」之间取平衡。
     *
     * @return 写入的总行数
     */
    public static long writeBatched(List<String> items, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须大于 0: " + batchSize);
        }
        long total = 0;
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            List<String> batch = items.subList(i, end);
            // 底层是「攒够一批才真的发」，一次调用写入半批/整批数据
            total += BATCH_SINK.apply(new ArrayList<>(batch));
        }
        return total;
    }

    /**
     * 压测对比：对同一份数据测「逐条」与「批量」各自的耗时与吞吐，并打印结论。
     * 数据量越大、单条固定开销越高，批量优势越明显（摊薄效应）。
     */
    public static void benchmarkDemo() {
        int count = 200;
        List<String> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add("item-" + i);
        }

        long t0 = System.nanoTime();
        long oneByOne = writeOneByOne(items);
        long t1 = System.nanoTime();
        long batched = writeBatched(items, 50);
        long t2 = System.nanoTime();

        double singleMs = (t1 - t0) / 1_000_000.0;
        double batchMs = (t2 - t1) / 1_000_000.0;
        System.out.printf("  逐条写入 %d 条: %.1f ms（%d 次底层调用）%n", count, singleMs, count);
        System.out.printf("  批量写入 %d 条: %.1f ms（%d 次底层调用）%n", count, batchMs, count / 50);
        System.out.printf("  批量快 %.1f 倍，写入行数一致=%b%n",
                singleMs / Math.max(batchMs, 0.0001), oneByOne == batched && batched == count);
    }

    /** 忙等睡眠：nanoTime 精度下最接近真实 I/O 耗时模拟（不抛 InterruptedException）。 */
    static void busySleep(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}