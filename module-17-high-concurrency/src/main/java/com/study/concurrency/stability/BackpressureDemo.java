package com.study.concurrency.stability;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 背压（Backpressure）演示：生产者太快时，用「有界队列 + 阻塞投递」让生产者慢下来，
 * 而不是让队列无限膨胀把内存打爆。
 *
 * <p>问题背景：高并发写入（写入 MQ / 批量落库 / 日志采集）如果不限队列大小，
 * 生产者秒杀级速率会把队列撑到内存耗尽（OOM），或堆积了海量任务导致停机时
 * 「排空队列」也要跑几小时。背压 = 消费不过来时反过来压住生产者：
 * <ul>
 *   <li>有界队列 + 阻塞 put：队列满，生产者阻塞（或超时放弃），消费速率成为生产速率上限；</li>
 *   <li>信号量限流：限制「同时在飞」的任务数，超过就 acquire 等待——本质是推模式背压。</li>
 * </ul>
 *
 * <p>容易错的地方：无界队列（LinkedBlockingQueue 默认无界）看着省事，是 OOM 的常见入口；
 * 阻塞投递要配超时，否则消费者故障会让生产者永久卡死；背压信号要对调用方透明（等待而非报错）。
 */
public final class BackpressureDemo {

    private BackpressureDemo() {
    }

    /** 演示有界队列：生产 10 条、队列容量 2、消费每条耗时 30ms，观察最大积压恒 ≤ 2。 */
    public static void boundedQueueDemo() throws InterruptedException {
        System.out.println("【有界队列背压演示】队列容量 2，生产者 10 条拼命塞，消费者 30ms/条");
        int capacity = 2;
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(capacity);
        AtomicLong maxQueueObserved = new AtomicLong();
        AtomicInteger produced = new AtomicInteger();

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    Integer item = queue.take(); // 队列空时消费者阻塞等待
                    System.out.println("    消费 " + item + "（积压剩 " + queue.size() + "）");
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 演示结束由主线程中断退出
            }
        }, "consumer");
        consumer.start();

        for (int i = 0; i < 10; i++) {
            // put 是背压核心：队列满时生产者在这里阻塞，而不是无限往无界队列里堆
            queue.put(i);
            produced.incrementAndGet();
            maxQueueObserved.accumulateAndGet(queue.size(), Math::max);
        }
        consumer.interrupt();
        consumer.join();
        System.out.println("  生产了 " + produced.get() + " 条，观察到的最大积压="
                + maxQueueObserved.get() + "（恒 ≤ 容量 " + capacity + "，内存有界）");
    }

    /**
     * 信号量限流（in-flight limiter，Hystrix 语义）：提交流程是
     * 「先 acquire 一个许可 → 提交给线程池 → 任务完成后 finally release」。
     * 正在跑 + 排队等待的任务数永远不会超过 maxInFlight：超过时生产者阻塞在 acquire
     * 上——把压力挡在提交侧，而不是让队列无限堆积。
     *
     * @return 实际执行的任务数（等待中的也会全部执行，只是放慢了生产节奏）
     */
    public static int semaphoreBackpressure(int maxInFlight, int totalTasks) throws InterruptedException {
        Semaphore gate = new Semaphore(maxInFlight); // 同时在飞（执行中+排队中）上限
        AtomicInteger executed = new AtomicInteger();
        CountDownLatch allDone = new CountDownLatch(totalTasks);
        // 消费者线程数 = 在飞上限：每个许可都能立刻被消费，不会堆积空闲线程
        ExecutorService workers = Executors.newFixedThreadPool(maxInFlight, runnable -> {
            Thread t = new Thread(runnable, "worker-backpressure");
            t.setDaemon(true); // 演示用守护线程：主流程结束后不阻止 JVM 退出
            return t;
        });
        long t0 = System.nanoTime();
        for (int i = 0; i < totalTasks; i++) {
            gate.acquire(); // 背压核心：在飞任务到上限时，生产者在这里阻塞等名额
            workers.submit(() -> {
                try {
                    executed.incrementAndGet();
                    Thread.sleep(10);
                    allDone.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    gate.release(); // 任务完成归还许可，等名额的生产者才能继续投
                }
            });
        }
        allDone.await(30, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        workers.shutdownNow();
        System.out.println("  在飞上限=" + maxInFlight + "，执行任务=" + executed.get()
                + "，总耗时=" + elapsedMs + "ms（任务被限速但一个不丢）");
        return executed.get();
    }

    /** 演示两个背压机制。 */
    public static void demo() throws InterruptedException {
        boundedQueueDemo();
        System.out.println("【信号量背压演示】在飞上限 2，共 30 个任务");
        semaphoreBackpressure(2, 30);
    }
}