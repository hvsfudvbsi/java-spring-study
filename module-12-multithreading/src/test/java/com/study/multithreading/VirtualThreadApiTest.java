package com.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 虚拟线程行为测试：创建方式、轻量性、守护属性、中断、自动编号、执行器
 */
class VirtualThreadApiTest {

    @Test
    @DisplayName("startVirtualThread：虚拟线程运行且 isVirtual=true，join 后 TERMINATED")
    void startVirtualThread() throws InterruptedException {
        AtomicBoolean ran = new AtomicBoolean();
        Thread vt = Thread.startVirtualThread(() -> ran.set(true));
        assertTrue(vt.isVirtual(), "startVirtualThread 创建的应是虚拟线程");
        vt.join(2_000);
        assertTrue(ran.get());
        assertEquals(Thread.State.TERMINATED, vt.getState());
    }

    @Test
    @DisplayName("ofVirtual().name()：构建器命名，同一构建器自动递增编号")
    void ofVirtualBuilder() throws InterruptedException {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("vt-", 0L);
        Thread t0 = builder.start(() -> {});
        Thread t1 = builder.start(() -> {});
        t0.join(2_000);
        t1.join(2_000);
        assertEquals("vt-0", t0.getName());
        assertEquals("vt-1", t1.getName());
        assertTrue(t0.isVirtual());
    }

    @Test
    @DisplayName("虚拟线程默认是守护线程，JVM 不等待")
    void virtualThreadIsDaemon() throws InterruptedException {
        Thread vt = Thread.ofVirtual().name("daemon-check").start(() -> {});
        vt.join(2_000);
        assertTrue(vt.isDaemon(), "虚拟线程默认 isDaemon=true");
    }

    @Test
    @DisplayName("interrupt：虚拟线程同样响应协作式中断")
    void interruptWorks() throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread vt = Thread.ofVirtual().name("interrupt-check").start(() -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        Thread.sleep(20);
        vt.interrupt();
        vt.join(2_000);
        assertTrue(interrupted.get(), "虚拟线程应收到中断");
        assertEquals(Thread.State.TERMINATED, vt.getState());
    }

    @Test
    @DisplayName("newVirtualThreadPerTaskExecutor：任务在虚拟线程上执行且结果正确")
    void perTaskExecutor() throws Exception {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            int tasks = 200;
            AtomicInteger sum = new AtomicInteger();
            AtomicBoolean allVirtual = new AtomicBoolean(true);
            CountDownLatch done = new CountDownLatch(tasks);
            for (int i = 0; i < tasks; i++) {
                int n = i;
                pool.submit(() -> {
                    if (!Thread.currentThread().isVirtual()) {
                        allVirtual.set(false);
                    }
                    sum.addAndGet(n);
                    done.countDown();
                });
            }
            assertTrue(done.await(5, TimeUnit.SECONDS), "任务应在 5 秒内全部完成");
            assertTrue(allVirtual.get(), "执行器应使用虚拟线程执行任务");
            assertEquals(tasks * (tasks - 1) / 2, sum.get());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("大量虚拟线程：5000 个各阻塞 20ms，全部完成且开销可忽略")
    void manyVirtualThreads() throws InterruptedException {
        int count = 5_000;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        for (Thread t : threads) {
            t.join(5_000);
            assertEquals(Thread.State.TERMINATED, t.getState(), "虚拟线程应全部结束");
        }
    }

    @Test
    @DisplayName("ThreadLocal 在虚拟线程间相互隔离（每线程独立副本）")
    void threadLocalIsolated() throws InterruptedException {
        ThreadLocal<String> ctx = new ThreadLocal<>();
        ctx.set("main");
        AtomicBoolean childValue = new AtomicBoolean(false);
        Thread vt = Thread.ofVirtual().start(() -> childValue.set(ctx.get() == null));
        vt.join(2_000);
        assertTrue(childValue.get(), "子虚拟线程应读不到主线程的 ThreadLocal（null）");
        assertEquals("main", ctx.get(), "主线程的值不受影响");
    }
}
