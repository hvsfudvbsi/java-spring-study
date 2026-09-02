package com.study.concurrency;

import com.study.concurrency.pool.DynamicThreadPool;
import com.study.concurrency.pool.ThreadPoolBestPractices;
import com.study.concurrency.ratelimit.CircuitBreaker;
import com.study.concurrency.ratelimit.LeakyBucket;
import com.study.concurrency.ratelimit.SlidingWindowCounter;
import com.study.concurrency.ratelimit.TokenBucket;
import com.study.concurrency.stability.BackpressureDemo;
import com.study.concurrency.stability.BulkheadExecutor;
import com.study.concurrency.stability.GracefulShutdownDemo;
import com.study.concurrency.stability.TimeoutControl;
import com.study.concurrency.tps.ZeroCopyDemo;

/**
 * module-17 高并发实战 · 总演示入口。
 *
 * <p>运行（在仓库根目录）：
 * <pre>
 * mvn compile exec:java -pl module-17-high-concurrency -Dexec.mainClass=com.study.concurrency.Main
 * </pre>
 *
 * <p>章节：
 * <ol>
 *   <li>线程池调优：动态参数调整 + 监控指标（{@link com.study.concurrency.pool}）</li>
 *   <li>线程池最佳实践：命名 / 异常兜底 / 四种拒绝策略 / 核心线程超时</li>
 *   <li>TPS 提升：批量处理（{@link com.study.concurrency.tps.BatchProcessor}）、
 *       零拷贝（{@link ZeroCopyDemo}）、连接池复用（{@link com.study.concurrency.tps.SimpleConnectionPool}）</li>
 *   <li>限流：令牌桶 / 漏桶 / 滑动窗口（{@link com.study.concurrency.ratelimit}）</li>
 *   <li>熔断器：Closed → Open → Half-Open（{@link CircuitBreaker}）</li>
 *   <li>稳定性：优雅停机 / 资源隔离 / 超时控制 / 背压（{@link com.study.concurrency.stability}）</li>
 * </ol>
 *
 * <p>量化对比请跑 {@link com.study.concurrency.bench.BenchmarkDemo}。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" module-17 高并发实战演示");
        System.out.println("========================================");

        // 每节独立 try/catch：某一节演示出错不影响后续章节，也避免异常导致线程池不回收
        runSection("线程池调优：动态参数调整 + 监控", DynamicThreadPool::demo);
        runSection("线程池最佳实践", ThreadPoolBestPractices::demo);
        runSection("TPS 提升：零拷贝", () -> ZeroCopyDemo.demo());
        runSection("限流", () -> {
            TokenBucket.demo();
            System.out.println();
            LeakyBucket.demo();
            System.out.println();
            SlidingWindowCounter.demo();
        });
        runSection("熔断", CircuitBreaker::demo);
        runSection("稳定性", () -> {
            GracefulShutdownDemo.demo();
            System.out.println();
            BulkheadExecutor.demo();
            System.out.println();
            TimeoutControl.demo();
            System.out.println();
            BackpressureDemo.demo();
        });

        System.out.println("\n========================================");
        System.out.println(" 演示结束（更多量化对比见 bench.BenchmarkDemo）");
        System.out.println("========================================");
    }

    /** 允许抛受检异常的演示段。 */
    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    /** 跑一节演示：异常打出来继续，避免整场演示中断。 */
    private static void runSection(String name, CheckedRunnable section) {
        System.out.println("\n[" + name + "]");
        try {
            section.run();
        } catch (Exception e) {
            System.out.println("  ⚠ 本节演示异常（不影响后续章节）: " + e);
        }
    }
}