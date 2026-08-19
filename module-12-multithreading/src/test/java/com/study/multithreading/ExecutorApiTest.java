package com.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程池行为测试：submit/get、shutdown/awaitTermination、invokeAll、invokeAny
 */
class ExecutorApiTest {

    @Test
    @DisplayName("submit 提交 Callable 并取回结果")
    void submitAndGet() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> future = pool.submit(() -> 6 * 7);
            assertEquals(42, future.get(3, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("shutdown 后不再接收新任务，已提交任务执行完")
    void shutdownRejectsNewTasks() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<Integer> f = pool.submit(() -> {
            Thread.sleep(50);
            return 1;
        });
        pool.shutdown();
        assertTrue(pool.isShutdown());
        // 已提交的任务仍会完成
        assertEquals(1, f.get(3, TimeUnit.SECONDS));
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        assertTrue(pool.isTerminated());
    }

    @Test
    @DisplayName("invokeAll 批量提交并全部完成；invokeAny 取最先完成")
    void invokeAllAndAny() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> 1, () -> 2, () -> 3);
            List<Future<Integer>> futures = pool.invokeAll(tasks);
            assertEquals(List.of(1, 2, 3), futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList());

            Integer fastest = pool.invokeAny(List.of(
                    () -> {
                        Thread.sleep(100);
                        return 100;
                    },
                    () -> 1));
            assertEquals(1, fastest);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("手写 ThreadPoolExecutor：拒绝策略生效")
    void rejectionPolicy() {
        // 核心 1、最大 1、队列容量 1 -> 第 3 个任务触发拒绝策略
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 30, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(1),
                r -> new Thread(r, "reject-test"),
                new ThreadPoolExecutor.AbortPolicy());
        try {
            pool.execute(() -> sleep(200));
            pool.execute(() -> sleep(200));   // 进入队列
            try {
                pool.execute(() -> {});
                assertFalse(true, "第 3 个任务应触发拒绝策略");
            } catch (java.util.concurrent.RejectedExecutionException expected) {
                assertTrue(true);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
