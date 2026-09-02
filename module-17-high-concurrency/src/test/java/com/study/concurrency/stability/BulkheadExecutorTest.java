package com.study.concurrency.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkheadExecutorTest {

    @Test
    @DisplayName("资源隔离: pizza 池（容量 1）被占满时，bank 池立刻执行不被拖累")
    void saturatedBulkheadDoesNotBlockOthers() throws Exception {
        CountDownLatch pizzaBusy = new CountDownLatch(1);
        CountDownLatch pizzaBlock = new CountDownLatch(1);
        CountDownLatch bankDone = new CountDownLatch(1);

        try (BulkheadExecutor bulkhead = new BulkheadExecutor(1)) {
            bulkhead.execute("pizza", () -> {
                pizzaBusy.countDown();
                try {
                    pizzaBlock.await(); // 占住 pizza 隔舱的唯一线程
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(pizzaBusy.await(2, TimeUnit.SECONDS));
            assertEquals(1, bulkhead.activeCount("pizza"), "pizza 隔舱已满");

            long t0 = System.nanoTime();
            bulkhead.execute("bank", bankDone::countDown);
            assertTrue(bankDone.await(1, TimeUnit.SECONDS), "bank 任务必须立刻执行");
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(elapsedMs < 500, "bank 在 pizza 占满的情况下仍应在 500ms 内完成，实际 " + elapsedMs + "ms");
            pizzaBlock.countDown();
        }
    }

    @Test
    @DisplayName("同一依赖复用同一个池: 同名投递不重复建池，第 3 个排队")
    void sameDependencySharesPool() throws Exception {
        CountDownLatch twoRunning = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (BulkheadExecutor bulkhead = new BulkheadExecutor(2)) {
            for (int i = 0; i < 3; i++) {
                bulkhead.execute("pay", () -> {
                    twoRunning.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertTrue(twoRunning.await(2, TimeUnit.SECONDS), "前 2 个任务开始跑");
            assertEquals(2, bulkhead.activeCount("pay"), "前 2 个在跑，第 3 个排队");
            release.countDown();
        }
    }
}