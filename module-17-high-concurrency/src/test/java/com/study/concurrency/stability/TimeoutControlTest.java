package com.study.concurrency.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeoutControlTest {

    @Test
    @DisplayName("正常调用: 任务在超时内完成，返回结果")
    void returnsValueWithinTimeout() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            String result = TimeoutControl.callWithTimeout(pool, () -> {
                Thread.sleep(50);
                return "ok";
            }, 1000);
            assertEquals("ok", result);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("超时调用: 任务超 300ms 后抛 TimeoutException，且底层任务被取消（收到中断）")
    void timesOutAndCancelsUnderlyingTask() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            AtomicBoolean interrupted = new AtomicBoolean();
            assertThrows(TimeoutException.class, () -> TimeoutControl.callWithTimeout(pool, () -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    interrupted.set(true); // cancel(true) 的中断真实送达
                    Thread.currentThread().interrupt();
                }
                return "never";
            }, 100));
            // 轮询等待中断送达（cancel 是异步的，需要一点时间）
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!interrupted.get() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(interrupted.get(), "底层慢任务应被 interrupt 取消");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("虚拟线程版: 正常调用返回结果")
    void virtualThreadReturnsValue() throws Exception {
        // 每个调用独立虚拟线程，互不干扰
        assertEquals("vt", TimeoutControl.callWithTimeoutVirtual(() -> {
            Thread.sleep(30);
            return "vt";
        }, 1000));
    }

    @Test
    @DisplayName("虚拟线程版: 超时抛 TimeoutException 且任务被取消")
    void virtualThreadTimesOutAndCancels() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        assertThrows(TimeoutException.class, () -> TimeoutControl.callWithTimeoutVirtual(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return "never";
        }, 50));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!interrupted.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(interrupted.get(), "虚拟线程上的慢任务应被中断");
    }
}