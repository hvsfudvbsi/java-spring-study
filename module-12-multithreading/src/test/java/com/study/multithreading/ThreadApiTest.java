package com.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thread 核心行为测试：join 等待、interrupt 协作式中断、holdsLock 判断
 */
class ThreadApiTest {

    @Test
    @DisplayName("join 等待子线程执行完毕")
    void joinWaits() throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sb.append("done");
        }, "join-test");
        t.start();
        t.join(2_000);
        assertEquals("done", sb.toString());
        assertEquals(Thread.State.TERMINATED, t.getState());
    }

    @Test
    @DisplayName("interrupt 协作式中断：自旋线程检测到中断标志后退出")
    void interruptStopsSpin() throws InterruptedException {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
        }, "spin-test");
        t.start();
        Thread.sleep(20);
        t.interrupt();
        t.join(2_000);
        assertEquals(Thread.State.TERMINATED, t.getState(), "中断后线程应退出");
        assertTrue(t.isInterrupted());
    }

    @Test
    @DisplayName("holdsLock 判断当前线程是否持有对象锁")
    void holdsLock() {
        Object lock = new Object();
        assertFalse(Thread.holdsLock(lock));
        synchronized (lock) {
            assertTrue(Thread.holdsLock(lock));
        }
        assertFalse(Thread.holdsLock(lock));
    }
}
