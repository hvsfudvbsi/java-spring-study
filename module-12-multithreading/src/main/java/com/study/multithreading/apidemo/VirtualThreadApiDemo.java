package com.study.multithreading.apidemo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 虚拟线程（Virtual Threads，JDK 21 正式版）方法用例（常用 + 不常用）
 *
 * 为什么需要虚拟线程（面试必问）：
 *   平台线程（Platform Thread）  = 1:1 映射操作系统线程，创建成本高（默认栈 1MB）、数量有限
 *   虚拟线程（Virtual Thread）   = 由 JVM 调度的轻量线程，挂在少量"载体线程"（carrier，即平台线程）上
 *   阻塞时自动让出 carrier（挂起不占线程）-> 可以"每任务一线程"地编程，I/O 密集场景吞吐暴增
 *
 * 与平台线程对比：
 *   | 维度       | 平台线程                  | 虚拟线程                      |
 *   |-----------|--------------------------|------------------------------|
 *   | 映射       | 1 线程 : 1 OS 线程        | 多线程 : 少量 carrier         |
 *   | 创建成本   | 高（毫秒级、栈 1MB）       | 极低（微秒级、可创建百万级）   |
 *   | 阻塞       | 占用 OS 线程              | 自动让出 carrier，不占线程     |
 *   | 适用场景   | CPU 密集 / 少量线程        | I/O 密集（网络、DB、文件）     |
 *
 * 核心 API：
 *   常用  ：Thread.startVirtualThread / Thread.ofVirtual().name() / Executors.newVirtualThreadPerTaskExecutor
 *   不常用：Thread.Builder 构建器 / ofVirtual().factory() / unstarted / pinned 现象 / 平台线程 vs 虚拟线程对比
 */
public class VirtualThreadApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 虚拟线程创建（常用） ==========");

        // ---- 1. Thread.startVirtualThread：最简创建方式 ----
        Thread vt1 = Thread.startVirtualThread(() ->
                System.out.println("  startVirtualThread 运行于: " + Thread.currentThread().getName()
                        + "（未命名时为空）"));
        vt1.join();
        System.out.println("  isVirtual()=" + vt1.isVirtual()
                + "，isDaemon()=" + vt1.isDaemon() + "（虚拟线程默认是守护线程，JVM 不等待）");

        // ---- 2. Thread.ofVirtual().name()：构建器 + 线程名 ----
        Thread vt2 = Thread.ofVirtual().name("vt-order").start(() ->
                System.out.println("  ofVirtual().name() 线程名: " + Thread.currentThread().getName()));
        vt2.join();

        // ---- 3. 自动编号：同一个构建器连续 start，名字自动递增 ----
        Thread.Builder.OfVirtual numbered = Thread.ofVirtual().name("vt-", 0L);
        Thread n0 = numbered.start(() -> {});
        Thread n1 = numbered.start(() -> {});
        n0.join();
        n1.join();
        System.out.println("  同一构建器自动编号: " + n0.getName() + " / " + n1.getName());

        // ---- 4. uncaughtExceptionHandler：线程内异常兜底（与平台线程一致） ----
        Thread vt3 = Thread.ofVirtual()
                .name("vt-risk")
                .uncaughtExceptionHandler((th, e) ->
                        System.out.println("  虚拟线程异常兜底: " + e.getMessage()))
                .start(() -> {
                    throw new IllegalStateException("虚拟线程业务异常");
                });
        vt3.join();

        // ---- 5. Executors.newVirtualThreadPerTaskExecutor：每任务一线程的执行器 ----
        ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            CountDownLatch done = new CountDownLatch(5);
            for (int i = 0; i < 5; i++) {
                int id = i;
                vtPool.submit(() -> {
                    System.out.println("  执行器任务 " + id + " 于 " + Thread.currentThread().getName()
                            + "（执行器创建的虚拟线程，默认未命名）");
                    done.countDown();
                });
            }
            done.await();
        } finally {
            vtPool.shutdown();
        }

        // ---- 6. 批量创建 10 万个虚拟线程：验证"轻量"（平台线程这个量级早 OOM 了） ----
        int bulkCount = 100_000;
        Thread.Builder.OfVirtual bulk = Thread.ofVirtual().name("bulk-", 0L);
        Thread[] bulkThreads = new Thread[bulkCount];
        long start = System.nanoTime();
        for (int i = 0; i < bulkCount; i++) {
            bulkThreads[i] = bulk.start(() -> {
                try {
                    Thread.sleep(10);   // 模拟阻塞 I/O
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        for (Thread th : bulkThreads) {
            th.join();
        }
        long bulkMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("  创建并跑完 " + bulkCount + " 个虚拟线程（每个 sleep 10ms），耗时 " + bulkMs + " ms");

        System.out.println();
        System.out.println("========== 虚拟线程不常用但有用的方法 ==========");

        // ---- Thread.Builder：平台线程 / 虚拟线程共用的构建器 ----
        Thread platform = Thread.ofPlatform().name("plat-1").start(() -> {});
        platform.join();
        System.out.println("  ofPlatform() 构建平台线程: " + platform.getName()
                + "，isVirtual()=" + platform.isVirtual());

        // ---- ofVirtual().factory()：生成 ThreadFactory（配合 Executors / 第三方库） ----
        ThreadFactory vtFactory = Thread.ofVirtual().name("vt-factory-", 0L).factory();
        ExecutorService factoryPool = Executors.newThreadPerTaskExecutor(vtFactory);
        CountDownLatch fDone = new CountDownLatch(2);
        factoryPool.submit(() -> {
            System.out.println("  ThreadFactory 创建: " + Thread.currentThread().getName());
            fDone.countDown();
        });
        factoryPool.submit(() -> {
            System.out.println("  ThreadFactory 创建: " + Thread.currentThread().getName());
            fDone.countDown();
        });
        fDone.await();
        factoryPool.shutdown();

        // ---- unstarted：只构建不启动，之后手动 start ----
        Thread lazy = Thread.ofVirtual().name("vt-lazy").unstarted(() ->
                System.out.println("  unstarted 构建后 start() 才执行"));
        System.out.println("  unstarted 构建后 state=" + lazy.getState() + "（NEW）");
        lazy.start();
        lazy.join();

        // ---- 中断与状态机：与平台线程完全一致（协作式中断） ----
        Thread vtState = Thread.ofVirtual().name("vt-state").start(() -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                System.out.println("  interrupt() 对虚拟线程同样生效（协作式中断）");
            }
        });
        System.out.println("  虚拟线程状态: " + vtState.getState());
        vtState.interrupt();
        vtState.join();
        System.out.println("  中断后状态: " + vtState.getState() + "（TERMINATED）");

        // ---- pinned 现象：锁内阻塞会占用 carrier（虚拟线程的"坑"） ----
        System.out.println("  pinned 现象：虚拟线程在锁内做阻塞会占用底层 carrier 线程，削弱并发优势");
        System.out.println("    （JDK 21 中 synchronized 与 ReentrantLock 都会 pin；JDK 24+ 的 ReentrantLock 已不 pin）");
        System.out.println("    规避：不要在虚拟线程的锁内做阻塞 I/O；阻塞操作放锁外");

        System.out.println();
        System.out.println("========== 实战对比：平台线程池 vs 虚拟线程 ==========");

        // I/O 密集任务：固定线程池受线程数限制，虚拟线程阻塞不占线程 -> 吞吐差距明显
        int tasks = 10_000;
        int ioMillis = 30;
        long fixedMs = runFixedPool(tasks, 200, ioMillis);
        long virtualMs = runVirtual(tasks, ioMillis);
        System.out.println("  固定线程池(200) 处理 " + tasks + " 个 I/O 任务: " + fixedMs + " ms");
        System.out.println("  虚拟线程        处理 " + tasks + " 个 I/O 任务: " + virtualMs + " ms");
        System.out.println("  （I/O 密集场景虚拟线程明显更快：阻塞时让出 carrier，不占线程）");
    }

    /** 固定线程池执行 N 个阻塞任务，返回耗时(ms) */
    static long runFixedPool(int tasks, int poolSize, int ioMillis) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch allDone = new CountDownLatch(tasks);
        long start = System.nanoTime();
        for (int i = 0; i < tasks; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(ioMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }
        allDone.await();
        pool.shutdown();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /** 虚拟线程执行 N 个阻塞任务，返回耗时(ms) */
    static long runVirtual(int tasks, int ioMillis) throws InterruptedException {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch allDone = new CountDownLatch(tasks);
        long start = System.nanoTime();
        for (int i = 0; i < tasks; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(ioMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
        }
        allDone.await();
        pool.shutdown();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
