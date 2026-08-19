package com.study.netty.apidemo;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ChannelFuture / Promise 方法用例（常用 + 不常用）
 *
 * Netty 异步模型核心（面试必问）：
 *   - 所有 IO 操作都是异步的，立即返回 Future，结果稍后到达
 *   - Future   只读结果（isDone/isSuccess/get...）
 *   - Promise  Future + 可写结果（setSuccess/setFailure...）
 *   - Listener 回调式（推荐，非阻塞） vs sync/await 阻塞式
 *
 * 两类典型用法：
 *   channel.writeAndFlush(msg).addListener(f -> ...)   // 回调（推荐）
 *   channel.closeFuture().sync()                       // 阻塞（主线程等退出）
 */
public class FutureApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== Future/Promise 常用方法 ==========");

        EventLoopGroup group = new NioEventLoopGroup(1);
        EventLoop loop = group.next();
        Promise<String> promise = new DefaultPromise<>(loop);

        // ---- addListener：回调式获取结果（推荐，不阻塞） ----
        CountDownLatch latch = new CountDownLatch(1);
        promise.addListener((GenericFutureListener<Future<String>>) f -> {
            System.out.println("  监听器回调（线程: " + Thread.currentThread().getName()
                    + "）: 成功=" + f.isSuccess() + ", 结果=" + f.getNow());
            latch.countDown();
        });

        // 完成 promise（生产代码中由异步 IO 事件触发，这里模拟）
        promise.setSuccess("异步结果");
        latch.await();

        // ---- sync：阻塞等待（简单粗暴） ----
        Promise<String> p2 = new DefaultPromise<>(loop);
        p2.setSuccess("值2");
        p2.sync();                    // 等待完成（失败会抛异常）
        System.out.println("  sync() 后 getNow=" + p2.getNow());

        // ---- isDone / isSuccess / cause ----
        Promise<String> p3 = new DefaultPromise<>(loop);
        p3.setFailure(new RuntimeException("模拟失败"));
        System.out.println("  isDone=" + p3.isDone()
                + ", isSuccess=" + p3.isSuccess()
                + ", cause=" + p3.cause().getMessage());

        System.out.println();
        System.out.println("========== Future/Promise 不常用但有用的方法 ==========");

        // ---- await / awaitUninterruptibly：带超时等待 ----
        Promise<String> p4 = new DefaultPromise<>(loop);
        p4.await(1, TimeUnit.SECONDS);              // 最多等 1 秒
        p4.awaitUninterruptibly(1, TimeUnit.SECONDS); // 不响应中断
        System.out.println("  await(超时) 后 isDone=" + p4.isDone());

        // ---- getNow / get(超时) / removeListener ----
        Promise<String> p5 = new DefaultPromise<>(loop);
        p5.setSuccess("立即值");
        p5.getNow();                       // 立即取（未完成返回 null，不阻塞）
        p5.get(1, TimeUnit.SECONDS);       // 带超时的 get
        GenericFutureListener<Future<String>> l = f -> System.out.println("  监听器触发");
        p5.addListener(l);
        p5.removeListener(l);              // 移除监听器
        System.out.println("  getNow=" + p5.getNow() + "，removeListener 已移除");

        // ---- cancel / isCancelled ----
        Promise<String> p6 = new DefaultPromise<>(loop);
        p6.cancel(true);                   // 尝试取消
        System.out.println("  cancel(true) 后 isCancelled=" + p6.isCancelled());

        // ---- syncUninterruptibly：不响应中断的阻塞 ----
        Promise<String> p7 = new DefaultPromise<>(loop);
        p7.setSuccess("ok");
        p7.syncUninterruptibly();
        System.out.println("  syncUninterruptibly 完成");

        // ---- 多监听器：一个 Future 可以挂多个监听器（组合的替代方案） ----
        Promise<String> multi = new DefaultPromise<>(loop);
        multi.addListener(f -> System.out.println("  监听器1 收到: " + f.getNow()));
        multi.addListener(f -> System.out.println("  监听器2 收到: " + f.getNow()));
        multi.setSuccess("多监听器");
        multi.syncUninterruptibly();

        group.shutdownGracefully().syncUninterruptibly();
    }
}
