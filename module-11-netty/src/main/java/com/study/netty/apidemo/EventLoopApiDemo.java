package com.study.netty.apidemo;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * EventLoop / EventLoopGroup 方法用例（常用 + 不常用）
 *
 * 线程模型（Netty 核心，面试必问）：
 *   EventLoopGroup（线程池）
 *     └── 多个 EventLoop（线程）
 *           └── 每个 EventLoop 绑定多个 Channel（一个线程处理多个连接）
 *
 * 铁律：
 *   1. 一个 Channel 的所有 IO 事件都在同一个 EventLoop 线程执行（无锁设计）
 *   2. 不要在 handler 中做耗时操作（会阻塞该线程上的所有连接）
 *   3. 跨线程操作 Channel 时用 eventLoop.execute() 提交任务
 */
public class EventLoopApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== EventLoopGroup 常用方法 ==========");

        // 创建线程池：参数=线程数（默认 CPU 核数*2）
        EventLoopGroup group = new NioEventLoopGroup(2);
        EventLoop loop = group.next();   // 取一个 EventLoop（负载均衡选择）

        System.out.println("  当前线程=" + Thread.currentThread().getName()
                + "，取到的 EventLoop=" + loop);

        // ---- execute：提交任务（在 EventLoop 线程执行，不阻塞当前线程） ----
        CountDownLatch latch1 = new CountDownLatch(1);
        loop.execute(() -> {
            System.out.println("  execute 任务在 " + Thread.currentThread().getName() + " 执行");
            latch1.countDown();
        });
        latch1.await(); // 等待任务执行完（演示用；真实场景无需等待）

        // ---- submit：提交有返回值的任务 ----
        var future = loop.submit(() -> "submit 返回值");
        System.out.println("  " + future.get());

        // ---- schedule：延迟执行一次 ----
        ScheduledFuture<?> once = loop.schedule(
                () -> System.out.println("  schedule 延迟 300ms 执行"),
                300, TimeUnit.MILLISECONDS);
        once.get(); // 等待完成

        // ---- scheduleAtFixedRate：固定频率（无论上次是否完成） ----
        CountDownLatch latch2 = new CountDownLatch(3);
        ScheduledFuture<?> rate = loop.scheduleAtFixedRate(() -> {
            System.out.println("  scheduleAtFixedRate 心跳 #" + latch2.getCount());
            latch2.countDown();
        }, 0, 200, TimeUnit.MILLISECONDS);
        latch2.await(3, TimeUnit.SECONDS);
        rate.cancel(false); // 取消定时任务

        System.out.println();
        System.out.println("========== EventLoop 不常用但有用的方法 ==========");

        // ---- scheduleWithFixedDelay：上次执行完再延迟（任务耗时长用这个） ----
        CountDownLatch latch3 = new CountDownLatch(1);
        ScheduledFuture<?> delay = loop.scheduleWithFixedDelay(() -> {
            System.out.println("  scheduleWithFixedDelay 执行（耗时任务用固定延迟）");
            latch3.countDown();
        }, 0, 500, TimeUnit.MILLISECONDS);
        latch3.await(3, TimeUnit.SECONDS);
        delay.cancel(false);

        // ---- inEventLoop：判断是否在 EventLoop 线程（线程安全操作的关键判断） ----
        boolean inLoop = loop.inEventLoop();
        System.out.println("  主线程 inEventLoop() = " + inLoop + "（false 表示需要提交任务）");
        loop.execute(() -> System.out.println("  EventLoop 线程内 inEventLoop() = " + loop.inEventLoop()));

        // ---- parent / next ----
        System.out.println("  loop.parent() = " + (loop.parent() == group)); // 所属线程池
        System.out.println("  group.next() 从线程池取下一个 EventLoop（负载均衡）");

        // ---- 生命周期方法 ----
        System.out.println("  isShutdown=" + group.isShutdown()
                + ", isShuttingDown=" + group.isShuttingDown()
                + ", isTerminated=" + group.isTerminated());

        // 优雅关闭：先停止接收新任务，再等已提交任务完成（生产环境必用）
        group.shutdownGracefully();               // 返回 Future，可等待
        boolean terminated = group.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  shutdownGracefully 后 isTerminated=" + terminated);
    }
}
