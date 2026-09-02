package com.study.concurrency.tps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimpleConnectionPoolTest {

    /** 用计数器工厂：每次真正「创建」都 +1，从而断言复用次数。 */
    private static SimpleConnectionPool<String> countingPool(AtomicInteger created, int maxSize) {
        return new SimpleConnectionPool<>(() -> {
            created.incrementAndGet();
            return "conn-" + created.get();
        }, maxSize);
    }

    @Test
    @DisplayName("顺序借还 10 次: 只创建 1 个连接，其余 9 次全部复用")
    void reuseInsteadOfCreate() throws Exception {
        AtomicInteger created = new AtomicInteger();
        try (SimpleConnectionPool<String> pool = countingPool(created, 2)) {
            for (int i = 0; i < 10; i++) {
                try (var lease = pool.acquire(100)) {
                    lease.get(); // 使用连接
                }
            }
            var stats = pool.stats();
            assertEquals(1, created.get(), "始终只有 1 个连接被创建");
            assertEquals(9, stats.reused());
            assertEquals(0, stats.activeCount());
            assertEquals(1, stats.idleCount());
        }
    }

    @Test
    @DisplayName("并发: 池容量 2 时 20 个并发任务全部借还成功，创建数不超过 2")
    void concurrentBorrowingBoundedByCapacity() throws Exception {
        AtomicInteger created = new AtomicInteger();
        try (SimpleConnectionPool<String> pool = countingPool(created, 2)) {
            int threads = 20;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            List<Thread> workers = new ArrayList<>();
            AtomicInteger failures = new AtomicInteger();
            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(() -> {
                    try {
                        start.await();
                        try (var lease = pool.acquire(2000)) {
                            Thread.sleep(10); // 模拟使用
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
                t.start();
                workers.add(t);
            }
            start.countDown();
            done.await(10, TimeUnit.SECONDS);
            for (Thread t : workers) {
                t.join(1000);
            }
            assertEquals(0, failures.get(), "所有并发借还都应成功");
            assertTrue(created.get() <= 2, "创建数受池容量约束: " + created.get());
        }
    }

    @Test
    @DisplayName("池满等待: 两个连接被长期占用，第 3 个 acquire 超时失败")
    void acquireTimesOutWhenPoolExhausted() throws Exception {
        try (SimpleConnectionPool<String> pool = countingPool(new AtomicInteger(), 2)) {
            var lease1 = pool.acquire(0);
            var lease2 = pool.acquire(0);
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> pool.acquire(100), "等 100ms 拿不到连接应超时");
            lease1.close();
            lease2.close();
        }
    }

    @Test
    @DisplayName("坏连接: 标记 broken 后不会放回池子，下次 acquire 新建")
    void brokenConnectionIsDiscarded() throws Exception {
        AtomicInteger created = new AtomicInteger();
        try (SimpleConnectionPool<String> pool = countingPool(created, 2)) {
            var lease = pool.acquire(0);
            assertEquals("conn-1", lease.get());
            lease.markBroken();
            lease.close();

            var next = pool.acquire(0);
            assertEquals(2, created.get(), "坏连接被丢弃，重新创建了新的");
            assertEquals(0, pool.stats().idleCount());
            next.close();
        }
    }

    @Test
    @DisplayName("关闭后 acquire 拒绝")
    void closedPoolRejectsAcquire() throws Exception {
        SimpleConnectionPool<String> pool = countingPool(new AtomicInteger(), 2);
        pool.close();
        try {
            pool.acquire(0);
            fail("关闭后 acquire 应抛 IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("已关闭"));
        }
    }
}