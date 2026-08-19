package com.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CompletableFuture 行为测试：链式转换、并行合并、allOf、超时兜底、getNow
 */
class CompletableFutureApiTest {

    @Test
    @DisplayName("thenApply 链式转换")
    void thenApplyChain() {
        String result = CompletableFuture.supplyAsync(() -> 21)
                .thenApply(n -> n * 2)
                .thenApply(String::valueOf)
                .join();
        assertEquals("42", result);
    }

    @Test
    @DisplayName("thenCombine 两个任务并行合并")
    void thenCombine() {
        CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> "A");
        CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> "B");
        assertEquals("A+B", a.thenCombine(b, (x, y) -> x + "+" + y).join());
    }

    @Test
    @DisplayName("allOf 等待全部完成；anyOf 取最先完成")
    void allOfAndAnyOf() throws Exception {
        CompletableFuture<Integer> c1 = CompletableFuture.supplyAsync(() -> 1);
        CompletableFuture<Integer> c2 = CompletableFuture.supplyAsync(() -> 2);
        CompletableFuture.allOf(c1, c2).get(3, TimeUnit.SECONDS);
        assertEquals(3, c1.join() + c2.join());

        Object fastest = CompletableFuture.anyOf(
                        CompletableFuture.supplyAsync(() -> {
                            sleep(100);
                            return "slow";
                        }),
                        CompletableFuture.supplyAsync(() -> "fast"))
                .get(3, TimeUnit.SECONDS);
        assertEquals("fast", fastest);
    }

    @Test
    @DisplayName("exceptionally 异常兜底；handle 双分支")
    void exceptionHandling() {
        String fallback = CompletableFuture.<String>supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                })
                .exceptionally(e -> "fallback")
                .join();
        assertEquals("fallback", fallback);

        String handled = CompletableFuture.<String>supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                })
                // 注意：异步任务抛出的异常会被包装成 CompletionException，需 getCause() 取真实异常
                .handle((r, e) -> e == null ? r : "handled:" + rootCause(e).getClass().getSimpleName())
                .join();
        assertEquals("handled:IllegalStateException", handled);
    }

    @Test
    @DisplayName("orTimeout 超时触发异常；completeOnTimeout 超时给默认值")
    void timeout() {
        CompletableFuture<String> timedOut = CompletableFuture.supplyAsync(() -> {
                    sleep(500);
                    return "late";
                })
                .orTimeout(100, TimeUnit.MILLISECONDS);
        // join() 阻塞到完成：100ms 后 orTimeout 使其异常完成（抛 CompletionException）
        try {
            timedOut.join();
        } catch (java.util.concurrent.CompletionException expected) {
            // 预期：超时导致异常完成
        }
        assertTrue(timedOut.isCompletedExceptionally());

        String defaulted = CompletableFuture.supplyAsync(() -> {
                    sleep(500);
                    return "late";
                })
                .completeOnTimeout("default", 100, TimeUnit.MILLISECONDS)
                .join();
        assertEquals("default", defaulted);
    }

    @Test
    @DisplayName("getNow 不阻塞：已完成返回结果，未完成返回兜底")
    void getNow() {
        CompletableFuture<Integer> done = CompletableFuture.completedFuture(7);
        assertEquals(7, done.getNow(-1));

        CompletableFuture<Integer> pending = new CompletableFuture<>();
        assertEquals(-1, pending.getNow(-1));   // 未完成 -> 兜底，不阻塞
        pending.complete(9);
        assertEquals(9, pending.join());
    }

    @Test
    @DisplayName("thenCompose 串行依赖，避免 Future 嵌套")
    void thenCompose() {
        Integer result = CompletableFuture.supplyAsync(() -> 3)
                .thenCompose(n -> CompletableFuture.supplyAsync(() -> n * n))
                .join();
        assertEquals(9, result);
    }

    @Test
    @DisplayName("applyToEither 竞速取先完成结果")
    void applyToEither() {
        String result = CompletableFuture.supplyAsync(() -> {
                    sleep(100);
                    return "cache";
                })
                .applyToEither(CompletableFuture.supplyAsync(() -> "db"), r -> "use:" + r)
                .join();
        assertEquals("use:db", result);
    }

    @Test
    @DisplayName("runAsync 无返回值 join 返回 null")
    void runAsync() {
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {});
        assertNull(f.join());
    }

    /** 剥掉 CompletionException 包装，取真实异常（生产排查必备） */
    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
