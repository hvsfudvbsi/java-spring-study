package com.study.multithreading.apidemo;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 线程协作工具方法用例（常用 + 不常用）
 *
 * 选型（面试必问）：
 *   CountDownLatch  一个线程等 N 个线程完成（一次性的"倒计时门闩"）
 *   CyclicBarrier   N 个线程互相等齐再一起冲（可循环复用）
 *   Semaphore       限流信号量（同时最多 N 个线程进入临界区）
 *   Exchanger       两个线程交换数据
 *   Phaser          分阶段协作（CyclicBarrier 的进阶版，可动态增减参与者）
 */
public class CoordinationApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== CountDownLatch 常用方法 ==========");

        // 场景：主线程等 3 个"检查项"都完成后才继续（如多服务健康检查）
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 1; i <= 3; i++) {
            int id = i;
            new Thread(() -> {
                sleep(50L * id);
                System.out.println("  检查项 " + id + " 完成");
                latch.countDown();          // 倒计时 -1
            }, "check-" + i).start();
        }
        latch.await();                       // 阻塞直到计数归零（可传超时 await(5, SECONDS)）
        System.out.println("  countDown 归零后主线程继续（latch.getCount()=" + latch.getCount() + "）");

        System.out.println();
        System.out.println("========== CountDownLatch 不常用但有用的方法 ==========");

        // ---- await(超时)：限时等待，超时返回 false（避免无限阻塞） ----
        CountDownLatch never = new CountDownLatch(1);
        boolean done = never.await(100, TimeUnit.MILLISECONDS);
        System.out.println("  await(100ms) 超时返回=" + done + "（CountDownLatch 不可重置，一次性）");

        System.out.println();
        System.out.println("========== CyclicBarrier 常用方法 ==========");

        // 场景：4 个选手都到齐才一起开跑（可复用：跑完一轮再来一轮）
        CyclicBarrier barrier = new CyclicBarrier(4, () ->
                System.out.println("  —— 4 个线程到齐，barrierAction 执行，一起出发 ——"));
        for (int i = 1; i <= 4; i++) {
            int id = i;
            new Thread(() -> {
                sleep(50L * (5 - id));       // 到达时间各不相同
                try {
                    System.out.println("  选手 " + id + " 到达起点");
                    barrier.await();         // 等其他人到齐
                    System.out.println("  选手 " + id + " 开跑");
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }, "runner-" + i).start();
        }
        sleep(600);   // 等第一轮跑完

        System.out.println();
        System.out.println("========== CyclicBarrier 不常用但有用的方法 ==========");

        // ---- 循环复用：计数归零后自动重置，可再来一轮 ----
        System.out.println("  CyclicBarrier 可复用：getParties=" + barrier.getParties()
                + "，getNumberWaiting=" + barrier.getNumberWaiting()
                + "，isBroken=" + barrier.isBroken());
        // ---- await(超时)：超时线程抛 TimeoutException，其余线程抛 BrokenBarrierException ----
        CyclicBarrier broken = new CyclicBarrier(3);
        new Thread(() -> {
            try {
                broken.await(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                System.out.println("  超时线程抛 " + e.getClass().getSimpleName() + "，栅栏损坏 isBroken=" + broken.isBroken());
            }
        }).start();
        sleep(300);

        System.out.println();
        System.out.println("========== Semaphore 常用方法 ==========");

        // 场景：停车场 3 个车位，5 辆车来停（限流）
        Semaphore parking = new Semaphore(3);
        for (int i = 1; i <= 5; i++) {
            int car = i;
            new Thread(() -> {
                try {
                    parking.acquire();       // 拿许可（没有就阻塞等）
                    System.out.println("  车 " + car + " 进场（剩余车位 " + parking.availablePermits() + "）");
                    sleep(100);
                    parking.release();       // 还许可
                    System.out.println("  车 " + car + " 出场");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "car-" + i).start();
        }
        sleep(700);

        System.out.println();
        System.out.println("========== Semaphore 不常用但有用的方法 ==========");

        // ---- tryAcquire：拿不到立即返回 false（不阻塞） ----
        Semaphore one = new Semaphore(1);
        one.acquire();
        System.out.println("  tryAcquire 无许可时=" + one.tryAcquire() + "（立即失败）");
        one.release();

        // ---- acquire(n)/release(n)：一次拿/还多个许可 ----
        Semaphore batch = new Semaphore(10);
        batch.acquire(3);
        System.out.println("  acquire(3) 后 availablePermits=" + batch.availablePermits());
        batch.release(3);

        // ---- drainPermits：一次性拿走所有剩余许可 ----
        int drained = batch.drainPermits();
        System.out.println("  drainPermits 一次性拿走=" + drained + "，剩余=" + batch.availablePermits());

        // ---- 公平信号量 + 等待队列 ----
        Semaphore fair = new Semaphore(1, true);
        System.out.println("  isFair=" + fair.isFair() + "，getQueueLength=" + fair.getQueueLength()
                + "（等待中的线程数）");

        System.out.println();
        System.out.println("========== Exchanger（不常用但有趣） ==========");

        // 场景：两个线程互相交换数据（如对账：一个线程收集，一个线程处理）
        Exchanger<String> exchanger = new Exchanger<>();
        Thread producer = new Thread(() -> {
            try {
                String received = exchanger.exchange("生产者数据");   // 阻塞直到对方也 exchange
                System.out.println("  生产者收到: " + received);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ex-producer");
        Thread consumer = new Thread(() -> {
            try {
                sleep(100);   // 稍晚到达，exchange 会互相等待
                String received = exchanger.exchange("消费者数据");
                System.out.println("  消费者收到: " + received);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ex-consumer");
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println();
        System.out.println("========== Phaser（进阶，不常用） ==========");

        // 场景：分 2 个阶段的任务，参与者可动态增减（CyclicBarrier 做不到）
        // 重要：所有参与者必须在启动线程前同步 register()，否则慢启动的任务会错过阶段推进
        Phaser phaser = new Phaser(1);        // 主线程先注册为第 1 个参与者
        for (int i = 1; i <= 3; i++) {
            phaser.register();                // 主线程替任务先注册（同步，避免竞态）
            int id = i;
            new Thread(() -> {
                try {
                    for (int phase = 1; phase <= 2; phase++) {
                        sleep(30L * id);
                        System.out.println("  任务 " + id + " 完成阶段 " + phase);
                        phaser.arriveAndAwaitAdvance();   // 到达并等待同阶段其他人
                    }
                } finally {
                    phaser.arriveAndDeregister();  // 任务结束动态注销（CyclicBarrier 做不到）
                }
            }, "task-" + i).start();
        }
        // 主线程也参与等待，直到所有阶段完成（主线程参与 -> 不会漏等）
        while (phaser.getPhase() < 2) {
            phaser.arriveAndAwaitAdvance();
        }
        System.out.println("  Phaser 全部阶段完成，getPhase=" + phaser.getPhase()
                + "，getRegisteredParties=" + phaser.getRegisteredParties());
        System.out.println("  arriveAndDeregister 可动态退出参与者（CyclicBarrier 不支持）");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
