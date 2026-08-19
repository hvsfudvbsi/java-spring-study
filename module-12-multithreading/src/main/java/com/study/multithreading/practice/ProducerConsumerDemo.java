package com.study.multithreading.practice;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实操示例一：生产者-消费者（订单处理系统）
 *
 * 场景：多个下单系统（生产者）把订单放进有界消息队列，
 *       多个履约系统（消费者）从队列取订单处理。
 *
 * 技术点：
 *   - ArrayBlockingQueue（有界队列）：put 满则阻塞、take 空则阻塞，天然解耦 + 削峰
 *   - 优雅停机：生产完成信号 + 消费者 poll(超时) 兜底退出，不丢订单
 *   - AtomicInteger 统计 + CountDownLatch 等待全部处理完
 *
 * 运行：mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.ProducerConsumerDemo
 */
public class ProducerConsumerDemo {

    /** 订单消息体（record：Java 16+） */
    record Order(int id, String item, int amount) {
    }

    /** 有界消息队列：容量 5，天然实现"削峰填谷" */
    static final BlockingQueue<Order> QUEUE = new ArrayBlockingQueue<>(5);

    static final int TOTAL_ORDERS = 20;      // 总订单数
    static final AtomicInteger NEXT_ORDER_ID = new AtomicInteger(1); // 订单号发号器
    static final AtomicInteger PRODUCED = new AtomicInteger();   // 已生产数（真实入队数）
    static final AtomicInteger PROCESSED = new AtomicInteger();  // 已处理数
    static volatile boolean productionDone = false;              // 生产完成信号

    public static void main(String[] args) throws Exception {
        System.out.println("========== 订单处理系统（生产者-消费者） ==========");
        System.out.println("  2 个生产者（下单系统） + 3 个消费者（履约系统），共 " + TOTAL_ORDERS + " 单");

        int producers = 2;
        int consumers = 3;
        CountDownLatch producersDone = new CountDownLatch(producers);
        CountDownLatch allProcessed = new CountDownLatch(1);

        // ---- 生产者：下单 ----
        for (int p = 0; p < producers; p++) {
            int producerId = p;
            new Thread(() -> {
                try {
                    while (true) {
                        int seq = NEXT_ORDER_ID.getAndIncrement();  // 取号
                        if (seq > TOTAL_ORDERS) {
                            break;   // 号已发完，订单生产完毕
                        }
                        Order order = new Order(seq, "商品-" + (seq % 5), 10 + seq);
                        QUEUE.put(order);   // 队列满则阻塞等待（削峰）
                        PRODUCED.incrementAndGet();   // 真实入队才计数
                        System.out.println("  [生产者" + producerId + "] 下单 " + order
                                + "（队列积压 " + QUEUE.size() + "）");
                        Thread.sleep(20);   // 模拟下单耗时
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            }, "producer-" + p).start();
        }

        // ---- 消费者：履约 ----
        for (int c = 0; c < consumers; c++) {
            int consumerId = c;
            new Thread(() -> {
                try {
                    while (true) {
                        // 超时取：队列空时最多等 100ms，避免生产结束后永远阻塞
                        Order order = QUEUE.poll(100, TimeUnit.MILLISECONDS);
                        if (order == null) {
                            if (productionDone) {
                                break;   // 生产结束且队列清空 -> 优雅退出
                            }
                            continue;
                        }
                        Thread.sleep(30);   // 模拟处理耗时
                        int done = PROCESSED.incrementAndGet();
                        System.out.println("    [消费者" + consumerId + "] 处理订单 #" + order.id()
                                + "（累计 " + done + "/" + TOTAL_ORDERS + "）");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "consumer-" + c).start();
        }

        // ---- 等待生产完成，然后发停止信号 ----
        producersDone.await();
        productionDone = true;
        System.out.println("  所有订单已生产完毕，等待消费者处理剩余队列...");

        // ---- 等待全部处理完（轮询 + 兜底超时） ----
        long deadline = System.currentTimeMillis() + 10_000;
        while (PROCESSED.get() < TOTAL_ORDERS && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        allProcessed.countDown();

        System.out.println("========================================");
        System.out.println("  结果：生产 " + PRODUCED.get() + " 单，处理 " + PROCESSED.get() + " 单");
        System.out.println("  队列剩余 " + QUEUE.size() + " 单"
                + (PROCESSED.get() == TOTAL_ORDERS ? " —— 全部处理完成，一单不丢 ✅" : " —— 有遗漏 ❌"));
    }
}
