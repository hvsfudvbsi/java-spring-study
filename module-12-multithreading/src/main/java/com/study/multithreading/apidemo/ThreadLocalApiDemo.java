package com.study.multithreading.apidemo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ThreadLocal 方法用例（常用 + 不常用）
 *
 * 作用：每个线程一份独立副本（线程隔离），常用于：
 *   - 一次请求内传递上下文（用户信息、traceId、事务连接）
 *   - SimpleDateFormat 线程不安全 -> 每个线程一个实例
 *
 * 原理（面试必问）：每个 Thread 内部有 ThreadLocalMap，key 是 ThreadLocal（弱引用）。
 *
 * 两大坑（必考）：
 *   1. 内存泄漏：key 是弱引用，value 是强引用 -> 线程池复用线程时旧值残留，务必 finally 中 remove()
 *   2. 线程池中"串数据"：线程复用导致上一个任务的 ThreadLocal 值被下一个任务读到
 */
public class ThreadLocalApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== ThreadLocal 常用方法 ==========");

        // ---- set / get：每个线程独立副本 ----
        ThreadLocal<String> userId = new ThreadLocal<>();
        userId.set("主线程用户");
        System.out.println("  主线程 get() = " + userId.get());

        Thread t = new Thread(() -> {
            // 子线程里 get 是 null（各线程互不干扰）
            System.out.println("  子线程 get() = " + userId.get() + "（null，每个线程独立副本）");
            userId.set("子线程用户");
            System.out.println("  子线程 set 后 get() = " + userId.get());
            userId.remove();
        });
        t.start();
        t.join();
        System.out.println("  主线程 get() 仍是 = " + userId.get() + "（子线程修改不影响）");

        // ---- withInitial：函数式初始化（Java 8+） ----
        ThreadLocal<Integer> counter = ThreadLocal.withInitial(() -> 0);
        System.out.println("  withInitial 首次 get() = " + counter.get() + "（自动执行初始化函数）");
        counter.set(100);
        System.out.println("  set(100) 后 get() = " + counter.get());

        // ---- 经典场景：每个线程一个 SimpleDateFormat（线程安全） ----
        ThreadLocal<java.text.SimpleDateFormat> dateFormat =
                ThreadLocal.withInitial(() -> new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        System.out.println("  ThreadLocal 包装线程不安全的 SimpleDateFormat: "
                + dateFormat.get().format(new java.util.Date()));

        System.out.println();
        System.out.println("========== ThreadLocal 不常用但有用的方法 ==========");

        // ---- remove：必须调用！防止内存泄漏（重点） ----
        // 注意：remove 必须在 finally 中调用，见下方"线程池坑"演示

        // ---- InheritableThreadLocal：子线程继承父线程的值 ----
        ThreadLocal<String> inheritable = new InheritableThreadLocal<>();
        inheritable.set("父线程配置");
        Thread child = new Thread(() ->
                System.out.println("  InheritableThreadLocal 子线程继承到: " + inheritable.get()));
        child.start();
        child.join();

        // ---- 线程池中的大坑：线程复用导致数据串线 ----
        ExecutorService pool = Executors.newFixedThreadPool(1);
        ThreadLocal<String> ctx = new ThreadLocal<>();
        pool.submit(() -> {
            ctx.set("任务A的上下文");
            System.out.println("  任务A 设置上下文");
        }).get();
        pool.submit(() -> {
            System.out.println("  任务B 读到残留上下文: " + ctx.get()
                    + "（！！！线程复用导致串数据，必须在 finally 中 remove）");
        }).get();
        pool.shutdown();

        // 正确写法：finally 中 remove
        ThreadLocal<String> safeCtx = new ThreadLocal<>();
        pool = Executors.newFixedThreadPool(1);
        pool.submit(() -> {
            try {
                safeCtx.set("任务A");
                System.out.println("  任务A（正确写法）设置上下文");
            } finally {
                safeCtx.remove();   // 用完必清
            }
        }).get();
        pool.submit(() -> System.out.println("  任务B（正确写法）读到: " + safeCtx.get() + "（null，已清理）")).get();
        pool.shutdown();

        // ---- ThreadLocalRandom：线程内独立随机数（比共享 Random 并发更快） ----
        int rand = ThreadLocalRandom.current().nextInt(100);
        System.out.println("  ThreadLocalRandom 随机数 = " + rand + "（并发场景别用共享 Random）");

        System.out.println();
        System.out.println("========== 面试必答：为什么 ThreadLocal 会内存泄漏 ==========");
        System.out.println("  Thread -> ThreadLocalMap(Entry key=ThreadLocal弱引用, value=强引用)");
        System.out.println("  key 被 GC 后 value 仍被线程持有 -> 线程池长生命周期线程不回收 -> 泄漏");
        System.out.println("  解法：用完 remove()；ThreadLocal 声明为 static final 弱化风险");
    }
}
