package com.study.multithreading.apidemo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池（ThreadPoolExecutor）方法用例（常用 + 不常用）
 *
 * 核心参数（构造器 7 参数，面试必问）：
 *   corePoolSize     核心线程数（常驻，不回收）
 *   maximumPoolSize  最大线程数
 *   keepAliveTime    非核心线程空闲存活时间
 *   workQueue        任务队列（有界/无界）
 *   线程工厂 + 拒绝策略（队列满且线程数到上限时的兜底）
 *
 * 执行流程：核心线程 -> 任务队列 -> 非核心线程 -> 拒绝策略
 *
 * 重要：阿里规约建议手写 ThreadPoolExecutor（明确参数），不要用 Executors 快捷方法
 * （newFixedThreadPool 无界队列可能 OOM；newCachedThreadPool 最大线程数 Integer.MAX_VALUE）。
 */
public class ExecutorApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 线程池创建（常用） ==========");

        // ---- Executors 快捷工厂（学习用，生产建议手写） ----
        ExecutorService fixed = Executors.newFixedThreadPool(4);      // 固定线程数
        ExecutorService cached = Executors.newCachedThreadPool();     // 弹性线程（空闲 60s 回收）
        ExecutorService single = Executors.newSingleThreadExecutor(); // 单线程（保证顺序）
        ExecutorService scheduled = Executors.newScheduledThreadPool(2); // 定时任务
        System.out.println("  newFixedThreadPool / newCachedThreadPool / newSingleThreadExecutor / newScheduledThreadPool");
        fixed.shutdown();
        cached.shutdown();
        single.shutdown();
        scheduled.shutdown();

        // ---- 手写 ThreadPoolExecutor（生产标准写法） ----
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,                          // corePoolSize：核心线程 2
                4,                          // maximumPoolSize：最大线程 4
                30, TimeUnit.SECONDS,       // keepAliveTime：非核心线程空闲 30s 回收
                new ArrayBlockingQueue<>(100),   // 有界任务队列（防止 OOM）
                r -> new Thread(r, "order-pool"),  // 线程工厂（起名方便排查）
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：队列满+线程满 -> 抛异常
        );

        System.out.println();
        System.out.println("========== 提交任务（常用） ==========");

        // ---- execute：提交无返回值任务 ----
        pool.execute(() -> System.out.println("  execute 任务运行于 " + Thread.currentThread().getName()));

        // ---- submit：提交有返回值任务（返回 Future） ----
        Future<Integer> f1 = pool.submit(() -> 1 + 1);
        System.out.println("  submit(Callable).get() = " + f1.get());

        // ---- invokeAll：批量提交并等待全部完成 ----
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            int n = i;
            tasks.add(() -> {
                Thread.sleep(50);
                return n * n;
            });
        }
        List<Future<Integer>> futures = pool.invokeAll(tasks);
        System.out.println("  invokeAll 结果 = " + futures.stream().map(ExecutorApiDemo::getQuietly).toList());

        // ---- invokeAny：返回最先完成的结果（谁先完成用谁） ----
        String first = pool.invokeAny(List.of(
                () -> {
                    Thread.sleep(200);
                    return "慢";
                },
                () -> {
                    Thread.sleep(50);
                    return "快";
                }));
        System.out.println("  invokeAny 取最先完成的结果 = " + first);

        System.out.println();
        System.out.println("========== 线程池状态（常用） ==========");
        System.out.println("  getPoolSize=" + pool.getPoolSize()
                + "，getActiveCount=" + pool.getActiveCount()
                + "，getTaskCount=" + pool.getTaskCount()
                + "，getCompletedTaskCount=" + pool.getCompletedTaskCount());

        // ---- 优雅关闭三部曲：shutdown -> awaitTermination ----
        pool.shutdown();   // 不再接收新任务，已提交任务继续执行
        boolean terminated = pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  shutdown + awaitTermination 后 isTerminated=" + terminated);

        System.out.println();
        System.out.println("========== 线程池不常用但有用的方法 ==========");

        ThreadPoolExecutor pool2 = new ThreadPoolExecutor(
                1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                r -> new Thread(r, "tune-pool"),
                new ThreadPoolExecutor.AbortPolicy());

        // ---- prestartCoreThread / prestartAllCoreThreads：提前创建核心线程（避免首次任务慢） ----
        boolean prestarted = pool2.prestartCoreThread();
        System.out.println("  prestartCoreThread 提前拉起一个核心线程=" + prestarted);
        pool2.prestartAllCoreThreads();
        System.out.println("  prestartAllCoreThreads 拉起全部核心线程（当前池大小=" + pool2.getPoolSize() + "）");

        // ---- allowCoreThreadTimeOut：核心线程空闲也回收（省资源） ----
        pool2.allowCoreThreadTimeOut(true);
        System.out.println("  allowCoreThreadTimeOut(true)：核心线程空闲同样超时回收");

        // ---- 动态调参：运行时改核心/最大线程数 ----
        // 注意顺序：先提高上限 maximumPoolSize，再提高 corePoolSize（核心线程数不能超过最大线程数）
        pool2.setMaximumPoolSize(6);
        pool2.setCorePoolSize(3);
        pool2.setKeepAliveTime(10, TimeUnit.SECONDS);
        System.out.println("  setCorePoolSize/setMaximumPoolSize/setKeepAliveTime 动态调参"
                + "（先 setMaximumPoolSize 再 setCorePoolSize，核心不能超过最大）");

        // ---- getQueue：拿到任务队列（监控积压量） ----
        System.out.println("  getQueue 剩余容量=" + pool2.getQueue().remainingCapacity());

        // ---- remove：从队列移除未开始的任务（取消排队任务） ----
        Runnable r = () -> System.out.println("  will be removed");
        pool2.execute(r);
        System.out.println("  remove(未执行任务)=" + pool2.remove(r) + "（从队列移除，未执行的任务可取消）");

        // ---- setRejectedExecutionHandler：运行时换拒绝策略 ----
        RejectedExecutionHandler callerRuns = new ThreadPoolExecutor.CallerRunsPolicy();
        pool2.setRejectedExecutionHandler(callerRuns);
        System.out.println("  setRejectedExecutionHandler 换成 CallerRunsPolicy（谁提交谁执行，不丢任务）");

        // ---- shutdownNow：立即停止，返回未执行的任务列表 ----
        List<Runnable> abandoned = pool2.shutdownNow();
        System.out.println("  shutdownNow 返回未执行任务数=" + abandoned.size() + "（正在执行的任务会被 interrupt）");

        // ---- purge：清理队列中已取消的 FutureTask ----
        // pool2.purge(); // 队列里有已 cancel 的任务时调用，防止"幽灵任务"占队列

        System.out.println();
        System.out.println("========== 拒绝策略（不常用但面试常考） ==========");
        System.out.println("  AbortPolicy        : 默认，抛 RejectedExecutionException");
        System.out.println("  CallerRunsPolicy   : 由提交任务的线程自己执行（不丢任务，降吞吐）");
        System.out.println("  DiscardPolicy      : 静默丢弃");
        System.out.println("  DiscardOldestPolicy: 丢弃队列里最老的任务，再提交新任务");
    }

    private static <T> T getQuietly(Future<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
