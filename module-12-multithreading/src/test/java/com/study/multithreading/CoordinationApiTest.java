package com.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程协作工具行为测试：CountDownLatch / CyclicBarrier / Semaphore
 */
class CoordinationApiTest {

    @Test
    @DisplayName("CountDownLatch：计数归零后 await 返回")
    void countDownLatch() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            new Thread(latch::countDown).start();
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(0, latch.getCount());
    }

    @Test
    @DisplayName("CountDownLatch 超时：计数未归零 await(超时) 返回 false")
    void countDownLatchTimeout() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("CyclicBarrier：4 线程到齐后同时放行，且可复用")
    void cyclicBarrier() throws Exception {
        int parties = 4;
        CyclicBarrier barrier = new CyclicBarrier(parties);
        AtomicInteger crossed = new AtomicInteger();

        Thread[] threads = new Thread[parties];
        for (int i = 0; i < parties; i++) {
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();   // 第一轮
                    crossed.incrementAndGet();
                    barrier.await();   // 第二轮（复用）
                    crossed.incrementAndGet();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join(3_000);
        }
        assertEquals(parties * 2, crossed.get(), "两轮各放行 4 个线程");
    }

    @Test
    @DisplayName("Semaphore：许可耗尽后 tryAcquire 失败，release 后恢复")
    void semaphore() throws InterruptedException {
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();
        assertFalse(semaphore.tryAcquire(), "许可耗尽，tryAcquire 应失败");
        semaphore.release();
        assertTrue(semaphore.tryAcquire(), "release 后应能再次获取");
        semaphore.release();
    }

    @Test
    @DisplayName("Semaphore：限流时同时最多 N 个线程进入临界区")
    void semaphoreLimit() throws Exception {
        int permits = 3;
        Semaphore semaphore = new Semaphore(permits);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    semaphore.acquire();
                    int now = inside.incrementAndGet();
                    maxInside.accumulateAndGet(now, Math::max);
                    Thread.sleep(20);
                    inside.decrementAndGet();
                    semaphore.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join(3_000);
        }
        assertTrue(maxInside.get() <= permits, "同时进入的线程数不能超过许可数");
    }
}
