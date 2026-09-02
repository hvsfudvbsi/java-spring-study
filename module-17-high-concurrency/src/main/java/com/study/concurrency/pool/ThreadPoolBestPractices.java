package com.study.concurrency.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池最佳实践：线程命名、未捕获异常处理、四种拒绝策略对比、核心线程超时。
 *
 * <p>学习目标：这些「看起来不起眼」的配置，在真实故障排查里都是救命稻草——
 * 没有线程名，jstack 出来全是 pool-1-thread-1 无法定位业务；没有 UncaughtExceptionHandler，
 * 线程池里的任务抛异常会静默丢失；拒绝策略选错，流量高峰会悄悄丢请求或把错误打到调用方线程。
 *
 * <p>拒绝策略触发时机：core 全忙 → 进队列 → 队列满 → 扩到 max → 再满才触发拒绝策略。
 * 四种策略（{@link ThreadPoolExecutor.AbortPolicy} 抛异常 / CallerRuns 调用方线程执行 /
 * Discard 静默丢弃 / DiscardOldest 丢队头最旧任务）语义差异见 {@link #rejectionDemo()}。
 */
public final class ThreadPoolBestPractices {

    private ThreadPoolBestPractices() {
    }

    /**
     * 带命名的线程工厂 + 未捕获异常处理器。
     *
     * @param prefix      线程名前缀，如 "pay-worker"
     * @param errorSink   线程内未捕获异常的落点（真实项目通常是日志框架），null 表示用默认打印
     * @return 可传给 Executors/ThreadPoolExecutor 的 ThreadFactory
     */
    public static ThreadFactory namedThreadFactory(String prefix,
                                                   java.util.function.Consumer<Throwable> errorSink) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + seq.getAndIncrement());
            // 给每个工作线程挂上全局异常兜底：任务抛 RuntimeException 时保证「有名字、有记录」，
            // 而不是线程静默死亡后任务凭空消失。
            t.setUncaughtExceptionHandler((thread, throwable) -> {
                if (errorSink != null) {
                    errorSink.accept(throwable);
                } else {
                    System.err.println("[" + thread.getName() + "] 任务异常退出: " + throwable);
                }
            });
            return t;
        };
    }

    /**
     * 演示四种拒绝策略在「core=1 max=1」下的差异。
     *
     * <p>观察点：Abort 抛 RejectedExecutionException；CallerRuns 由调用方线程（主线程）执行被拒任务；
     * Discard 任务静默消失；DiscardOldest 丢掉队列里最旧的任务、把新任务留下
     * （所以它需要真实队列才能演示——SynchronousQueue 无缓冲，DiscardOldest 会空转反复重试）。
     */
    public static void rejectionDemo() {
        System.out.println("【拒绝策略演示】core=1 max=1：先占住唯一工作线程，再提交更多任务");
        // SynchronousQueue 无缓冲：唯一线程忙时提交必被拒，最能体现「队列满+达 max → 拒绝策略」
        System.out.println("  · AbortPolicy（默认）: 队列满且达 max → 抛 RejectedExecutionException");
        demonstrate("abort", new SynchronousQueue<>(), 1, new ThreadPoolExecutor.AbortPolicy());
        System.out.println("  · CallerRunsPolicy: 被拒任务由调用方线程执行（天然背压）");
        demonstrate("caller-runs", new SynchronousQueue<>(), 1, new ThreadPoolExecutor.CallerRunsPolicy());
        System.out.println("  · DiscardPolicy: 静默丢弃，调用方无感知（可丢的任务才用它）");
        demonstrate("discard", new SynchronousQueue<>(), 1, new ThreadPoolExecutor.DiscardPolicy());
        System.out.println("  · DiscardOldestPolicy: 丢弃排队最旧的任务，新任务留下（配容量 1 的队列演示）");
        demonstrate("discard-oldest", new LinkedBlockingQueue<>(1), 2, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    /**
     * 在指定队列上演示一种拒绝策略：先占住唯一线程，再提交 extra 个任务，观察各自结局。
     * 第一个任务走「workerCount<core 直通创建线程」路径必然成功，避免与 SynchronousQueue 的空闲交接竞态。
     *
     * @param handler 该策略对应的拒绝处理器（Abort/CallerRuns/Discard/DiscardOldest）
     */
    private static void demonstrate(String policy, BlockingQueue<Runnable> queue, int extra,
                                    RejectedExecutionHandler handler) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, queue,
                namedThreadFactory("reject-" + policy, null), handler);
        try {
            pool.execute(() -> sleepQuietly(300)); // 占住唯一线程 300ms
            System.out.println("    [" + policy + "] 唯一线程已占住（睡 300ms）");
            for (int i = 0; i < extra; i++) {
                int idx = i;
                try {
                    pool.execute(() -> System.out.println("    [" + policy + "] 提交任务 #" + idx + " 被执行了"));
                    System.out.println("    [" + policy + "] 提交任务 #" + idx + " 入队/执行成功");
                } catch (RejectedExecutionException e) {
                    System.out.println("    [" + policy + "] 提交任务 #" + idx + " → 抛 RejectedExecutionException");
                }
            }
            System.out.println("    [" + policy + "] 池状态: 活跃=" + pool.getActiveCount()
                    + " 已完成=" + pool.getCompletedTaskCount() + " 队列=" + pool.getQueue().size());
        } finally {
            pool.shutdownNow(); // 无论上面怎样，池必须关，否则非守护线程让 JVM 无法退出
        }
    }

    /**
     * 核心线程超时回收：低峰期把「一直占着」的核心线程也释放（默认核心线程永不超时）。
     *
     * <p>注意：必须先调 setKeepAliveTime（设定空闲存活时长），再调 allowCoreThreadTimeOut(true)。
     * 演示：core=2 的池在无任务时，等 keepAlive 过后线程数会掉到 0。
     */
    public static void coreThreadTimeoutDemo() {
        System.out.println("【核心线程超时演示】core=2 keepAlive=1 秒，无任务后观察线程数收敛");
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory());
        pool.prestartAllCoreThreads();
        System.out.println("  预热后 poolSize=" + pool.getPoolSize() + "（2 个核心线程常驻）");
        pool.setKeepAliveTime(1, TimeUnit.SECONDS);
        pool.allowCoreThreadTimeOut(true);
        sleepQuietly(1600);
        System.out.println("  空闲 1.6 秒后 poolSize=" + pool.getPoolSize() + "（核心线程也被回收）");
        pool.shutdownNow();
    }

    /** 综合演示：命名工厂 + 拒绝策略对比 + 核心线程超时。 */
    public static void demo() {
        System.out.println("========================================");
        System.out.println(" 线程池最佳实践");
        System.out.println("========================================");
        rejectionDemo();
        coreThreadTimeoutDemo();

        System.out.println("【线程命名与异常兜底演示】提交一个会抛异常的任务，观察线程名与错误打印");
        // 构建一个 execute() 直接拒绝的池，仅为了观察命名工厂效果
        ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1), namedThreadFactory("pay-worker",
                e -> System.err.println("   [兜底] 未捕获异常: " + e)));
        pool.execute(() -> System.out.println("   任务在线程 " + Thread.currentThread().getName() + " 上执行"));
        pool.shutdown();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}