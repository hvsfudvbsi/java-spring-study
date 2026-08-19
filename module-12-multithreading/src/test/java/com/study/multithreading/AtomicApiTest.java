package com.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原子类行为测试：CAS 语义、并发自增正确性、ABA 解决
 */
class AtomicApiTest {

    @Test
    @DisplayName("CAS：期望值匹配才更新")
    void compareAndSet() {
        AtomicInteger ai = new AtomicInteger(10);
        assertTrue(ai.compareAndSet(10, 20));   // 期望值匹配 -> 更新
        assertEquals(20, ai.get());
        assertFalse(ai.compareAndSet(10, 99));  // 期望值不匹配 -> 失败
        assertEquals(20, ai.get());
    }

    @Test
    @DisplayName("并发自增：10 线程 x 1000 次 = 10000，无丢失")
    void concurrentIncrement() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 1000; j++) {
                        counter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(10_000, counter.get());
    }

    @Test
    @DisplayName("LongAdder 高并发累加正确")
    void longAdder() throws InterruptedException {
        LongAdder adder = new LongAdder();
        int threads = 8;
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    adder.increment();
                }
                done.countDown();
            }).start();
        }
        done.await();
        assertEquals(80_000, adder.sum());
        assertEquals(80_000, adder.sumThenReset());
        assertEquals(0, adder.sum());
    }

    @Test
    @DisplayName("AtomicReference 原子更新对象引用")
    void atomicReference() {
        AtomicReference<String> ref = new AtomicReference<>("A");
        assertTrue(ref.compareAndSet("A", "B"));
        assertEquals("B", ref.get());
        // updateAndGet 自旋更新
        assertEquals("B!", ref.updateAndGet(s -> s + "!"));
    }

    @Test
    @DisplayName("AtomicStampedReference：带版本号解决 ABA")
    void stampedReference() {
        AtomicStampedReference<String> ref = new AtomicStampedReference<>("A", 0);
        int[] stamp = new int[1];
        assertEquals("A", ref.get(stamp));
        assertEquals(0, stamp[0]);

        // 值 + 版本号同时匹配才更新
        assertTrue(ref.compareAndSet("A", "B", 0, 1));
        assertFalse(ref.compareAndSet("A", "C", 0, 1));   // 值已是 B
        assertEquals("B", ref.getReference());
        assertEquals(1, ref.getStamp());
    }
}
