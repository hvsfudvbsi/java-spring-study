package com.study.multithreading.apidemo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * 锁与同步方法用例（常用 + 不常用）
 *
 * synchronized vs Lock（面试必问）：
 *   | 维度       | synchronized          | ReentrantLock            |
 *   |-----------|----------------------|--------------------------|
 *   | 释放方式   | 自动（异常也释放）     | 必须手动 unlock（finally）|
 *   | 尝试获取   | 不支持               | tryLock 支持             |
 *   | 中断响应   | 不支持               | lockInterruptibly 支持   |
 *   | 公平性     | 非公平               | 可指定公平               |
 *   | 条件变量   | wait/notify          | newCondition 多条件       |
 *
 * 结论：简单场景用 synchronized，需要超时/中断/公平/多条件时用 Lock。
 */
public class SynchronizedLockDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== synchronized 常用方法 ==========");
        Counter counter = new Counter();
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    counter.increment();   // synchronized 方法
                }
            }, "t" + i);
            threads[i].start();
        }
        for (Thread th : threads) {
            th.join();
        }
        System.out.println("  synchronized 保护计数器：10 线程 x 1000 次 = " + counter.get() + "（期望 10000）");

        System.out.println();
        System.out.println("========== ReentrantLock 常用方法 ==========");

        ReentrantLock lock = new ReentrantLock();
        // ---- lock / unlock：必须成对，finally 中解锁 ----
        lock.lock();
        try {
            System.out.println("  lock.lock() 获取锁，holdCount=" + lock.getHoldCount());
            // 可重入：同一线程可再次获取
            lock.lock();
            try {
                System.out.println("  可重入后 holdCount=" + lock.getHoldCount());
            } finally {
                lock.unlock();
            }
        } finally {
            lock.unlock();
        }

        // ---- tryLock：拿不到锁立即返回 false（不阻塞） ----
        ReentrantLock l2 = new ReentrantLock();
        l2.lock();
        boolean got = l2.tryLock();
        System.out.println("  锁被占用时 tryLock()=" + got + "（立即失败，不阻塞）");
        // ---- tryLock(超时)：限时等待 ----
        boolean got2 = l2.tryLock(200, TimeUnit.MILLISECONDS);
        System.out.println("  tryLock(200ms) 超时=" + got2);
        l2.unlock();

        System.out.println();
        System.out.println("========== Lock 不常用但有用的方法 ==========");

        // ---- lockInterruptibly：等待锁时响应中断（避免"拿不到锁就卡死"） ----
        ReentrantLock l3 = new ReentrantLock();
        l3.lock();
        Thread waiter = new Thread(() -> {
            try {
                l3.lockInterruptibly();
                System.out.println("  waiter 拿到锁");
                l3.unlock();
            } catch (InterruptedException e) {
                System.out.println("  lockInterruptibly 等待中被 interrupt() 打断（不再傻等锁）");
            }
        }, "waiter");
        waiter.start();
        Thread.sleep(100);
        waiter.interrupt();
        waiter.join();
        l3.unlock();

        // ---- 锁状态检查（调试利器） ----
        System.out.println("  isLocked=" + l3.isLocked() + "，isHeldByCurrentThread=" + l3.isHeldByCurrentThread());
        System.out.println("  getQueueLength=" + l3.getQueueLength() + "（等待该锁的线程数）");

        // ---- 公平锁：new ReentrantLock(true)（先来先得，性能略低） ----
        ReentrantLock fair = new ReentrantLock(true);
        System.out.println("  isFair=" + fair.isFair());

        // ---- Condition：一个锁多个等待条件（替代 wait/notify，更精细） ----
        BoundedBuffer buffer = new BoundedBuffer(2);
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                buffer.put(i);
            }
        }, "producer");
        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                buffer.take();
            }
        }, "consumer");
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        // ---- ReentrantReadWriteLock：读读并发、读写互斥（读多写少场景） ----
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        rw.readLock().lock();
        System.out.println("  读锁可被多线程同时持有（readLock 不互斥）");
        rw.readLock().unlock();
        rw.writeLock().lock();
        System.out.println("  写锁独占：isWriteLocked=" + rw.isWriteLocked());
        rw.writeLock().unlock();

        // ---- StampedLock：乐观读（读多写极少时性能之王） ----
        StampedLock stamped = new StampedLock();
        long stamp = stamped.tryOptimisticRead();   // 乐观读：不真正加锁
        // ...读共享数据...
        if (stamped.validate(stamp)) {
            System.out.println("  乐观读 validate 通过（期间无人写，零锁开销）");
        } else {
            long rs = stamped.readLock();            // 被写了，升级为悲观读锁
            System.out.println("  乐观读失效，升级 readLock 重读");
            stamped.unlockRead(rs);
        }
        long ws = stamped.writeLock();
        System.out.println("  writeLock 获取成功（独占写）");
        stamped.unlockWrite(ws);
    }

    /** synchronized 计数器：方法级同步 */
    static class Counter {
        private int count;

        public synchronized void increment() {
            count++;
        }

        public synchronized int get() {
            return count;
        }
    }

    /** 用 Lock + 两个 Condition 实现有界缓冲区（生产者-消费者） */
    static class BoundedBuffer {
        private final Object[] items;
        private int putIndex, takeIndex, size;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedBuffer(int capacity) {
            items = new Object[capacity];
        }

        public void put(Object x) {
            lock.lock();
            try {
                while (size == items.length) {
                    notFull.await();   // 队列满：等待"不满"条件
                }
                items[putIndex] = x;
                putIndex = (putIndex + 1) % items.length;
                size++;
                System.out.println("  [Condition] put(" + x + ")，size=" + size);
                notEmpty.signal();     // 唤醒一个等待"非空"的消费者
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }

        public Object take() {
            lock.lock();
            try {
                while (size == 0) {
                    notEmpty.await();  // 队列空：等待"非空"条件
                }
                Object x = items[takeIndex];
                takeIndex = (takeIndex + 1) % items.length;
                size--;
                System.out.println("  [Condition] take()=" + x + "，size=" + size);
                notFull.signal();      // 唤醒一个等待"不满"的生产者
                return x;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                lock.unlock();
            }
        }
    }
}
