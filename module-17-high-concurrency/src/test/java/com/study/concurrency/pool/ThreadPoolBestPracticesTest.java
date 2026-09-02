package com.study.concurrency.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThreadPoolBestPracticesTest {

    @Test
    @DisplayName("命名工厂: 线程名带前缀且自带编号，便于 jstack 定位")
    void threadNamesCarryPrefix() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> name = new AtomicReference<>();
        ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1),
                ThreadPoolBestPractices.namedThreadFactory("pay-worker", null));
        pool.execute(() -> {
            name.set(Thread.currentThread().getName());
            started.countDown();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertEquals("pay-worker-1", name.get());
        pool.shutdownNow();
    }

    @Test
    @DisplayName("异常兜底: 任务抛 RuntimeException 时未捕获异常被交给 errorSink")
    void uncaughtExceptionReachesSink() throws Exception {
        CountDownLatch seen = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1),
                ThreadPoolBestPractices.namedThreadFactory("boom", e -> {
                    caught.set(e);
                    seen.countDown();
                }));
        pool.execute(() -> {
            throw new IllegalStateException("任务内部爆炸");
        });
        assertTrue(seen.await(1, TimeUnit.SECONDS),
                "errorSink 未在 1 秒内被调用（说明异常被静默吞掉了）");
        assertEquals("任务内部爆炸", caught.get().getMessage());
        pool.shutdownNow();
    }
}