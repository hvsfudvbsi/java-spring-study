package com.study.multithreading;

/**
 * 多线程与并发学习模块总入口
 *
 * 运行方式（IDEA 中右键 Run，或命令行）：
 *   mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.Main
 *
 * 本模块分两部分：
 *   1. API 方法用例（常用+不常用）：Thread / Runnable-Callable / 锁 / 线程池 / 原子类 /
 *      并发集合 / 阻塞队列 / CompletableFuture / 同步工具 / ThreadLocal / 虚拟线程
 *   2. 并发实操示例：生产者-消费者 / 抢票 / 银行转账 / 高并发请求（虚拟线程）
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  第一部分：API 方法用例（常用+不常用）");
        System.out.println("========================================");

        com.study.multithreading.apidemo.ThreadApiDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.RunnableCallableDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.SynchronizedLockDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.ExecutorApiDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.AtomicApiDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.ConcurrentCollectionDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.BlockingQueueDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.CompletableFutureApiDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.CoordinationApiDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.ThreadLocalApiDemo.main(args);
        System.out.println();

        com.study.multithreading.apidemo.VirtualThreadApiDemo.main(args);
        System.out.println();

        System.out.println("========================================");
        System.out.println("  第二部分：并发实操示例（可单独运行）");
        System.out.println("========================================");
        System.out.println("  1. 生产者-消费者 : com.study.multithreading.practice.ProducerConsumerDemo");
        System.out.println("  2. 抢票系统      : com.study.multithreading.practice.TicketSaleDemo");
        System.out.println("  3. 银行转账      : com.study.multithreading.practice.BankTransferDemo");
        System.out.println("  4. 高并发请求    : com.study.multithreading.practice.HighConcurrencyGatewayDemo");
        System.out.println("  详情见 module-12-multithreading/README.md");
    }
}
