package com.study.javabasics.concurrency;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发编程（Java 并发是面试重点中的重点）
 *
 * 核心概念：
 *   1. 线程创建：Thread / Runnable / Callable / ExecutorService（推荐线程池）
 *   2. 线程安全：synchronized / ReentrantLock / volatile / Atomic* / ConcurrentHashMap
 *   3. CompletableFuture：异步编排，组合多个异步任务（Java 8+，必学）
 *
 * 重要原则：
 *   - 永远不要手动 new Thread 裸奔，用线程池
 *   - 多线程修改共享变量要加锁或用原子类
 *   - 优先使用 ConcurrentHashMap 而不是 Hashtable/synchronizedMap
 */
public class ConcurrencyDemo {

    public static void demo() {
        System.out.println("【1. 线程池执行任务】");
        // 推荐用工厂方法创建线程池，不要用 new ThreadPoolExecutor 手写（除非你需要定制）
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<FutureTask> tasks = List.of(new FutureTask(1), new FutureTask(2), new FutureTask(3));
            List<java.util.concurrent.Future<Integer>> futures = tasks.stream()
                    .map(pool::submit)
                    .toList();
            futures.forEach(f -> {
                try {
                    System.out.println("   任务结果 = " + f.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } // try-with-resources 会自动 shutdown

        System.out.println();
        System.out.println("【2. 原子类 AtomicInteger：无锁线程安全计数器】");
        AtomicInteger counter = new AtomicInteger(0);
        List<Thread> threads = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        counter.incrementAndGet();
                    }
                }))
                .toList();
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        System.out.println("   10 个线程各加 1000 次，结果 = " + counter.get() + "（期望 10000）");

        System.out.println();
        System.out.println("【3. ConcurrentHashMap：并发安全的 Map】");
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("key", 1);
        map.computeIfAbsent("key", k -> 100); // key 已存在则忽略
        System.out.println("   computeIfAbsent 结果 = " + map.get("key"));

        System.out.println();
        System.out.println("【4. CompletableFuture：异步编排（重点）】");
        // 模拟两个异步任务然后合并结果
        CompletableFuture<String> taskA = CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "任务A结果";
        });
        CompletableFuture<String> taskB = CompletableFuture.supplyAsync(() -> {
            sleep(80);
            return "任务B结果";
        });

        // thenCombine：两个任务都完成后合并
        CompletableFuture<String> combined = taskA.thenCombine(taskB, (a, b) -> a + " + " + b);
        System.out.println("   thenCombine 合并 = " + combined.join());

        // 链式异步：thenApply 转换 -> thenAccept 消费（处理异常用 exceptionally/handle）
        CompletableFuture.supplyAsync(() -> 42)
                .thenApply(n -> n * 2)
                .thenApply(Object::toString)
                .thenAccept(s -> System.out.println("   异步链式结果 = " + s))
                .join();
    }

    record FutureTask(int id) implements java.util.concurrent.Callable<Integer> {
        @Override
        public Integer call() {
            sleep(20);
            return id * 10;
        }
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
