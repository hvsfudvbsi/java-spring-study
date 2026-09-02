package com.study.concurrency.stability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 资源隔离（Bulkhead，隔舱）演示：给每个外部依赖一个独立的小线程池，
 * 一个依赖的故障不会拖垮其他依赖。
 *
 * <p>名字来自轮船的隔舱：一个舱进水只淹一个舱，船不会沉。没用隔离时，所有请求共用
 * 一个大线程池，某个下游慢/挂（比如第三方支付接口超时 30 秒）会把线程池占满，
 * 其他本来健康的依赖（比如商品查询）也跟着全部超时——这就是「故障放大/雪崩」；
 * 用隔舱后，支付池满只影响支付，商品池照常服务，只是支付相关请求快速失败。
 *
 * <p>实现：{@link ConcurrentHashMap} 按资源名持有各自的 {@link ExecutorService}，
 * 获取时按需创建（computeIfAbsent），每个池有自己的线程数上限。
 *
 * <p>容易错的地方：隔舱数量/大小要按真实并发量估算（太小正常流量也会被挤爆、
 * 太大失去隔离意义）；用完要显式关闭优雅停机，否则线程泄漏。
 */
public final class BulkheadExecutor implements AutoCloseable {

    private final Map<String, ExecutorService> pools = new ConcurrentHashMap<>();
    private final int workersPerPool;
    private final AtomicInteger poolSeq = new AtomicInteger();

    /** @param workersPerPool 每个依赖分配的独立线程数（隔舱的「舱壁」厚度） */
    public BulkheadExecutor(int workersPerPool) {
        this.workersPerPool = workersPerPool;
    }

    /**
     * 往指定依赖的隔舱里投任务；该依赖的池不存在时自动创建。
     * 池满时任务进入各自隔舱的有界/无界队列——注意隔舱之间互不影响。
     */
    public void execute(String dependency, Runnable task) {
        ExecutorService pool = pools.computeIfAbsent(dependency,
                name -> Executors.newFixedThreadPool(workersPerPool,
                        runnable -> {
                            Thread t = new Thread(runnable,
                                    "bulkhead-" + name + "-" + poolSeq.incrementAndGet());
                            t.setDaemon(true); // 演示用守护线程，避免测试/演示后 JVM 不退出
                            return t;
                        }));
        pool.execute(task);
    }

    /** 指定依赖隔舱的繁忙度：活跃线程数（1=池满在跑，0=空闲）。 */
    public int activeCount(String dependency) {
        ExecutorService pool = pools.get(dependency);
        if (pool instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return 0;
    }

    @Override
    public void close() {
        pools.values().forEach(pool -> {
            pool.shutdownNow();
        });
        pools.values().forEach(pool -> {
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        pools.clear();
    }

    /**
     * 演示：pizza 池容量 1 被一个长任务占满，同时给 bank 池投任务 → bank 立刻被执行，
     * 证明「一个舱进水不影响另一个舱」。
     */
    public static void demo() throws InterruptedException {
        System.out.println("【资源隔离演示】每个依赖 1 个线程：pizza 池被长任务占满，bank 池照常服务");
        try (BulkheadExecutor bulkhead = new BulkheadExecutor(1)) {
            bulkhead.execute("pizza", () -> {
                System.out.println("    pizza 请求进入（睡 2 秒模拟慢下游）");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(100); // 确保 pizza 任务已占住唯一线程
            System.out.println("    pizza 池活跃=" + bulkhead.activeCount("pizza")
                    + "（已占满）");
            java.util.concurrent.CountDownLatch bankDone = new java.util.concurrent.CountDownLatch(1);
            long t0 = System.nanoTime();
            bulkhead.execute("bank", () -> {
                System.out.println("    bank 请求立刻执行（完全没被 pizza 拖累）");
                bankDone.countDown();
            });
            bankDone.await();
            System.out.printf("    bank 请求从投递到执行完成仅 %.0f ms%n",
                    (System.nanoTime() - t0) / 1_000_000.0);
            System.out.println("    若共用一个大池，bank 会被 pizza 占满的池饿死——这就是隔舱的价值");
        }
    }
}