package com.study.concurrency.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DynamicThreadPoolTest {

    @Test
    @DisplayName("饱和顺序: core 满→队列满→扩到 max→再满拒绝（core=2 max=4 队列=2 时多投 2 个被拒）")
    void saturationOrderRejectsAfterMax() throws Exception {
        CountDownLatch busy = new CountDownLatch(4); // 4 个在跑线程就位
        CountDownLatch release = new CountDownLatch(1); // 拦住任务不结束，保证确定性
        try (DynamicThreadPool pool = new DynamicThreadPool(2, 4, 2, 5, "sat")) {
            for (int i = 0; i < 8; i++) {
                try {
                    pool.execute(() -> {
                        busy.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (RejectedExecutionException expected) {
                    // 第 7、8 个任务必然走到拒绝策略（继续投以观测全部拒绝路径）
                }
            }
            assertTrue(busy.await(2, TimeUnit.SECONDS), "4 个线程应全部开始跑");
            var snap = pool.snapshot();
            assertEquals(4, snap.activeCount(), "2 核心 + 2 扩容 = 4 在跑");
            assertEquals(2, snap.queueSize(), "2 个排队任务占满队列");
            assertEquals(2, pool.rejectedCount(), "第 7、8 个任务被拒绝");
            assertEquals(4, snap.maximumPoolSize());
            release.countDown();
            assertTrue(pool.shutdownGracefully(5), "存量任务排空后优雅退出");
        }
    }

    @Test
    @DisplayName("动态扩容: 运行期把队列从 2 调到 6，已满队列立刻能再收任务")
    void dynamicQueueExpansion() throws Exception {
        CountDownLatch busy = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (DynamicThreadPool pool = new DynamicThreadPool(2, 2, 2, 5, "dyn")) {
            // 先只投 2 个阻塞任务，等它们真正把两个线程占住，再填充队列（避免预启动线程未就位的竞态）
            for (int i = 0; i < 2; i++) {
                pool.execute(() -> {
                    busy.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertTrue(busy.await(2, TimeUnit.SECONDS), "两个工作线程应都已忙");
            for (int i = 0; i < 2; i++) {
                pool.execute(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertEquals(2, pool.snapshot().queueSize(), "线程忙时新任务必进队列");

            pool.setQueueCapacity(6);
            assertEquals(0, pool.rejectedCount(), "队列扩到 6 前无拒绝");
            for (int i = 0; i < 4; i++) {
                pool.execute(() -> {
                }); // 扩容量后这些任务全部入队，不再拒绝
            }
            assertEquals(0, pool.rejectedCount(), "扩容量后新任务入队不再拒绝");
            release.countDown();
            assertTrue(pool.shutdownGracefully(5));
        }
    }

    @Test
    @DisplayName("缩容: 调小核心线程数后 snapshot 反映新值")
    void shrinkReflectedInSnapshot() {
        try (DynamicThreadPool pool = new DynamicThreadPool(4, 8, 16, 5, "shrink")) {
            pool.setCorePoolSize(1);
            assertEquals(1, pool.snapshot().corePoolSize());
        }
    }

    @Test
    @DisplayName("停机后提交抛 RejectedExecutionException")
    void submitAfterShutdownRejected() throws Exception {
        DynamicThreadPool pool = new DynamicThreadPool(1, 1, 1, 5, "stopped");
        assertTrue(pool.shutdownGracefully(5));
        assertThrows(RejectedExecutionException.class,
                () -> pool.execute(() -> {
                }), "停机后新任务直接走拒绝策略（计数+抛异常）");
        assertEquals(1, pool.rejectedCount());
    }

    @Test
    @DisplayName("预热: 创建池后线程立即拉到 core 个")
    void prestartCreatesCoreThreads() {
        try (DynamicThreadPool pool = new DynamicThreadPool(3, 5, 8, 5, "warm")) {
            assertTrue(pool.snapshot().poolSize() >= 3, "prestartAllCoreThreads 应立起 3 个");
        }
    }
}