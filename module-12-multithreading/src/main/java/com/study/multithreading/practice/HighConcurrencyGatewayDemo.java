package com.study.multithreading.practice;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 实操示例四：高并发请求处理（虚拟线程实战）
 *
 * 场景：模拟一个 I/O 密集的网关/HTTP 服务 —— 10,000 个并发请求，
 *       每个请求都要"调下游"（阻塞 30ms 模拟网络/数据库调用）。
 *
 * 两种实现对比：
 *   1. 固定线程池（200 个平台线程）：请求排队，吞吐受线程数上限限制
 *   2. 虚拟线程（每请求一线程）：阻塞时不占线程，吞吐显著更高，代码也更简单
 *
 * 结论（面试必问）：
 *   - I/O 密集场景（网络、数据库、文件）用虚拟线程：每任务一线程，阻塞自动让出 carrier
 *   - CPU 密集场景虚拟线程无优势：线程数 ≈ CPU 核数即可
 *   - 虚拟线程不是银弹：锁内阻塞会 pin 住 carrier（JDK 21 中 synchronized 与 ReentrantLock 都会）
 *
 * 运行：mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.HighConcurrencyGatewayDemo
 */
public class HighConcurrencyGatewayDemo {

    static final int REQUESTS = 10_000;   // 总请求数
    static final int IO_MILLIS = 30;      // 每个请求"调下游"的阻塞时长
    static final int POOL_SIZE = 200;     // 固定线程池大小（对比用）

    public static void main(String[] args) throws Exception {
        System.out.println("========== 高并发请求处理：固定线程池 vs 虚拟线程 ==========");
        System.out.println("  " + REQUESTS + " 个并发请求，每个阻塞 " + IO_MILLIS + "ms 模拟下游调用");

        long fixedMs = processWithFixedPool(REQUESTS, POOL_SIZE, IO_MILLIS);
        long virtualMs = processWithVirtualThreads(REQUESTS, IO_MILLIS);

        System.out.println("  [固定线程池 " + POOL_SIZE + " 线程] 耗时 " + fixedMs + " ms，成功 " + REQUESTS + "/" + REQUESTS);
        System.out.println("  [虚拟线程每请求一线程] 耗时 " + virtualMs + " ms，成功 " + REQUESTS + "/" + REQUESTS);
        System.out.println("  （I/O 密集场景：虚拟线程阻塞不占线程，吞吐明显更高，代码还更简单）");
    }

    /** 固定线程池处理：任务排队，线程数即并发上限 */
    public static long processWithFixedPool(int requests, int poolSize, int ioMillis) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch allDone = new CountDownLatch(requests);
        long start = System.nanoTime();
        for (int i = 0; i < requests; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    handleRequest(id, ioMillis);
                } finally {
                    allDone.countDown();
                }
            });
        }
        allDone.await();
        pool.shutdown();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /** 虚拟线程处理：每个请求一个虚拟线程，阻塞自动让出 carrier */
    public static long processWithVirtualThreads(int requests, int ioMillis) throws InterruptedException {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch allDone = new CountDownLatch(requests);
        long start = System.nanoTime();
        for (int i = 0; i < requests; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    handleRequest(id, ioMillis);
                } finally {
                    allDone.countDown();
                }
            });
        }
        allDone.await();
        pool.shutdown();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /** 模拟处理一个请求：先调下游（阻塞 I/O），再做一点本地计算 */
    static void handleRequest(int id, int ioMillis) {
        try {
            Thread.sleep(ioMillis);   // 模拟网络/数据库等阻塞 I/O
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 本地计算（CPU 密集部分很少，可忽略）
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += (id * 31 + i) % 7;
        }
        if (sum < 0) {
            throw new IllegalStateException("不可能发生");
        }
    }
}
