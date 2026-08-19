package com.study.multithreading.apidemo;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable / Callable / Future / FutureTask 方法用例（常用 + 不常用）
 *
 * 三种"任务"形态对比：
 *   Runnable.run()     -> 无返回值、不能抛受检异常
 *   Callable.call()    -> 有返回值、能抛受检异常
 *   Future.get()       -> 异步拿结果 / 等待 / 取消任务
 */
public class RunnableCallableDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== Runnable / Callable 常用方法 ==========");

        // ---- Runnable：无返回值任务 ----
        Runnable task = () -> System.out.println("  Runnable 执行于 " + Thread.currentThread().getName());
        new Thread(task, "runnable-thread").start();

        // ---- Callable：有返回值任务 ----
        Callable<String> callable = () -> {
            Thread.sleep(100);
            return "Callable 的返回值";
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // ---- Future.get()：阻塞等待结果 ----
        Future<String> future = pool.submit(callable);
        System.out.println("  future.get() = " + future.get());

        // ---- submit 传 Runnable：get() 返回 null，仅用于"等待完成" ----
        Future<?> runFuture = pool.submit(() -> System.out.println("  submit(Runnable) 执行"));
        runFuture.get();   // 返回 null

        // ---- FutureTask：把 Callable 包成 Runnable（可交给 Thread 或线程池） ----
        FutureTask<Integer> futureTask = new FutureTask<>(() -> 21 * 2);
        new Thread(futureTask, "futuretask-thread").start();
        System.out.println("  FutureTask.get() = " + futureTask.get());

        System.out.println();
        System.out.println("========== Future 不常用但有用的方法 ==========");

        // ---- get(timeout)：超时不再傻等 ----
        Future<String> slow = pool.submit(() -> {
            Thread.sleep(2_000);
            return "慢任务结果";
        });
        try {
            slow.get(300, TimeUnit.MILLISECONDS);   // 超时抛 TimeoutException
        } catch (TimeoutException e) {
            System.out.println("  get(300ms) 超时抛 TimeoutException（避免无限阻塞）");
        }
        slow.cancel(true);   // 取消任务，参数 true = 中断正在执行的线程
        System.out.println("  cancel(true) 后 isCancelled=" + slow.isCancelled()
                + "，isDone=" + slow.isDone());

        // ---- isDone：轮询任务是否完成（不阻塞） ----
        Future<String> done = pool.submit(() -> "立即完成");
        while (!done.isDone()) {
            Thread.onSpinWait();   // 忙等提示
        }
        System.out.println("  isDone 轮询到完成，结果=" + done.get());

        // ---- isCancelled：判断是否被取消 ----
        Future<String> cancelMe = pool.submit(() -> {
            Thread.sleep(5_000);
            return "x";
        });
        cancelMe.cancel(true);
        System.out.println("  isCancelled=" + cancelMe.isCancelled());

        System.out.println();
        System.out.println("========== ThreadFactory（不常用但生产必备） ==========");

        // ---- 自定义线程工厂：给线程起名、设守护、设异常兜底（排查问题靠它） ----
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread th = new Thread(r, "biz-pool-" + seq.getAndIncrement());
            th.setDaemon(false);
            th.setUncaughtExceptionHandler((t, e) ->
                    System.out.println("  [" + t.getName() + "] 异常: " + e.getMessage()));
            return th;
        };
        ExecutorService namedPool = Executors.newFixedThreadPool(2, factory);
        namedPool.submit(() -> System.out.println("  自定义线程工厂创建的线程: " + Thread.currentThread().getName()));
        namedPool.shutdown();

        // ---- 默认线程工厂（了解） ----
        ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        System.out.println("  defaultThreadFactory 命名规则: pool-N-thread-M（不方便排查，生产建议自定义）");

        pool.shutdown();
        System.out.println("  shutdown 后 awaitTermination=" + pool.awaitTermination(3, TimeUnit.SECONDS));
    }

    @SuppressWarnings("unused")
    private static void demoDeprecated() throws ExecutionException, InterruptedException {
        // 补充：Future.get() 抛出的异常包装在 ExecutionException 里，需要 getCause() 取真实异常
        Future<Integer> boom = Executors.newSingleThreadExecutor().submit(() -> {
            throw new IllegalArgumentException("计算失败");
        });
        try {
            boom.get();
        } catch (ExecutionException e) {
            System.out.println("  ExecutionException.getCause() = " + e.getCause().getMessage());
        }
    }
}
