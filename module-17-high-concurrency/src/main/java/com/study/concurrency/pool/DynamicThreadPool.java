package com.study.concurrency.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态线程池：核心线程数 / 最大线程数 / 队列容量都可在运行期调整，并提供监控指标快照。
 *
 * <p>学习目标：ThreadPoolExecutor 三个参数（core、max、队列）的配合关系，以及为什么
 * 「队列够大就不用 max」「max 只有在队列满时才有意义」——拒绝策略触发顺序是
 * {@code core 满 → 进队列 → 队列满 → 扩到 max → 再满才拒绝}。
 *
 * <p>现实场景：秒杀前把线程池调大（扩容预热），大促后调小省资源；监控线程定时采集
 * {@link #snapshot()} 观察活跃数、队列积压、拒绝数，辅助判断是否该扩容。
 *
 * <p>运行入口：{@link com.study.concurrency.pool.ThreadPoolBestPractices#demo()} 与本类
 * {@link #demo()}；观察点：调整容量后队列行为变化、拒绝计数单调递增、监控快照各字段。
 */
public final class DynamicThreadPool implements AutoCloseable {

    /**
     * 可动态扩容的有界队列：容量用 volatile 保存，setCapacity 可运行期修改。
     *
     * <p>为什么自己实现而不换队列：ThreadPoolExecutor 一旦创建无法替换工作队列，
     * 只能修改队列自身的容量语义；offer 返回 false 即「队列已满」，会触发线程池扩容/拒绝。
     */
    static final class ResizableLinkedQueue extends LinkedBlockingQueue<Runnable> {
        private static final long serialVersionUID = 1L;
        private volatile int capacity;

        ResizableLinkedQueue(int capacity) {
            super();
            this.capacity = capacity;
        }

        /**
         * 按当前容量拒绝入队。加锁是为了让容量修改与并发 offer 有确定次序（演示用，够用）。
         * 返回值语义与 ThreadPoolExecutor 契约一致：false 表示「队列满」→ 尝试扩 max 或拒绝。
         */
        @Override
        public synchronized boolean offer(Runnable e) {
            if (size() >= capacity) {
                return false;
            }
            return super.offer(e);
        }

        /** 运行期改容量（可扩可缩）；只影响之后入队判断，已排队的任务不受影响。 */
        synchronized void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        int capacity() {
            return capacity;
        }
    }

    /** 单次监控快照：record 保证不可变，便于采集线程直接使用。 */
    public record Snapshot(
            int corePoolSize,
            int maximumPoolSize,
            int poolSize,
            int activeCount,
            int queueCapacity,
            int queueSize,
            long completedTaskCount,
            long rejectedCount,
            long largestPoolSize) {
        /** 积压率：队列使用比例，>0.5 提示可能该扩容，=1 说明持续满队列。 */
        public double queueUsageRatio() {
            return queueCapacity == 0 ? 0 : queueSize / (double) queueCapacity;
        }
    }

    private final ThreadPoolExecutor executor;
    private final ResizableLinkedQueue queue;
    private final AtomicLong rejectedCount = new AtomicLong();

    /**
     * 创建动态线程池。
     *
     * @param corePoolSize    核心线程数：即便空闲也保留的线程（除非开启核心超时）
     * @param maximumPoolSize 最大线程数：队列满时才扩容到的上限
     * @param queueCapacity   队列容量：核心线程全忙时任务进队列，满了才扩 max
     * @param keepAliveSeconds 非核心线程空闲回收秒数
     * @param threadNamePrefix 线程名前缀，便于 jstack / 监控识别
     */
    public DynamicThreadPool(int corePoolSize, int maximumPoolSize, int queueCapacity,
                             long keepAliveSeconds, String threadNamePrefix) {
        this.queue = new ResizableLinkedQueue(queueCapacity);
        this.executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveSeconds,
                TimeUnit.SECONDS, queue, namedThreadFactory(threadNamePrefix),
                countingRejectedHandler());
        // 预热：提前创建 core 个线程，避免第一波请求撞上「创建线程」的延迟。
        executor.prestartAllCoreThreads();
    }

    /** 提交任务；返回 Future 调用方可在需要时获取结果/取消。 */
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    /** 无返回值地投递任务（配合监控看队列积压更直观）。 */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    /** 运行期调整核心/最大线程数与队列容量（一次调用同时改三个，扩容后的常见操作）。 */
    public void adjust(int corePoolSize, int maximumPoolSize, int queueCapacity) {
        // 先扩 max 再扩 core：若先扩 core 会立刻创建线程占名额，而先扩 max 只是放开门槛
        executor.setMaximumPoolSize(maximumPoolSize);
        executor.setCorePoolSize(corePoolSize);
        queue.setCapacity(queueCapacity);
    }

    /** 只改核心线程数（缩容场景：大促结束把常驻线程收回来）。 */
    public void setCorePoolSize(int corePoolSize) {
        executor.setCorePoolSize(corePoolSize);
    }

    /** 只改队列容量（积压预警后可临时扩队列缓冲）。 */
    public void setQueueCapacity(int capacity) {
        queue.setCapacity(capacity);
    }

    /** 开启核心线程超时回收：低峰期连核心线程也能释放（先 setKeepAliveTime 再 allowCoreThreadTimeOut）。 */
    public void allowCoreThreadTimeout(long keepAliveSeconds) {
        executor.setKeepAliveTime(keepAliveSeconds, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
    }

    /** 采集一次监控快照（采集线程自己调用，不影响工作线程）。 */
    public Snapshot snapshot() {
        return new Snapshot(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                queue.capacity(),
                queue.size(),
                executor.getCompletedTaskCount(),
                rejectedCount.get(),
                executor.getLargestPoolSize());
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    /** 优雅停机：先 shutdown（拒绝新任务、排空队列），再等待存量任务完成，超时返回是否全部结束。 */
    public boolean shutdownGracefully(long timeoutSeconds) {
        executor.shutdown();
        try {
            return executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    /** AutoCloseable：try-with-resources 里直接等待优雅停机完成（最多 10 秒）。 */
    @Override
    public void close() {
        shutdownGracefully(10);
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        // 自定义 ThreadFactory：给线程起有意义的名字，出问题能一眼从 jstack 定位到业务池
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + seq.getAndIncrement());
            // 业务线程池通常用非守护线程：JVM 退出前必须显式停机，避免任务被悄悄丢弃
            t.setDaemon(false);
            return t;
        };
    }

    private RejectedExecutionHandler countingRejectedHandler() {
        // ThreadPoolExecutor 在「队列满且达到 max」时才走到拒绝策略；
        // 这里包一层计数再抛默认异常，让拒绝次数可被监控到（容量规划的依据）。
        return (r, e) -> {
            rejectedCount.incrementAndGet();
            throw new java.util.concurrent.RejectedExecutionException(
                    "线程池已满: pool=" + e.getPoolSize() + " active=" + e.getActiveCount()
                            + " queue=" + queue.size() + "/" + queue.capacity());
        };
    }

    /** 不为空的任务（睡眠一段时间模拟真实工作），供演示与测试复用。 */
    static Runnable sleepingTask(String name, long millis) {
        return () -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /** 演示入口：看三个参数如何联动、拒绝什么时候发生、调整容量后行为如何变化。 */
    public static void demo() {
        System.out.println("【动态线程池演示】core=2 max=4 队列=2，投 8 个任务（每个睡 100ms）");
        DynamicThreadPool pool = new DynamicThreadPool(2, 4, 2, 5, "dynamic-demo");
        try {
            for (int i = 1; i <= 8; i++) {
                try {
                    pool.execute(sleepingTask("task-" + i, 100));
                } catch (RejectedExecutionException e) {
                    // 前 2 个占 core、第 3/4 个进队列、第 5/6 个扩容到 max=4，
                    // 第 7/8 个必然被拒——这正是要演示的「队列满 + max 满 → 拒绝」
                    System.out.println("    task-" + i + " 被拒绝（队列满且已达 max=4）");
                }
            }
            System.out.println("  投完后快照: " + pool.snapshot());
            System.out.println("  说明: poolSize=4 说明 2 个占满 core、2 个占满队列后扩容到 max=4；"
                    + "第 7/8 个任务只能被拒绝（rejectedCount=2）");
            System.out.println("  等待 300ms 让前一批任务完成并按 keepAlive 回收空闲线程…");
            sleepQuietly(300);
            System.out.println("  中间快照: " + pool.snapshot());
            System.out.println("  把队列容量从 2 扩到 6，再投 4 个任务（应全部进队列不再拒绝）");
            pool.setQueueCapacity(6);
            for (int i = 9; i <= 12; i++) {
                pool.execute(sleepingTask("task-" + i, 100));
            }
            System.out.println("  扩队列后快照: " + pool.snapshot());
            System.out.println("  等待全部结束…");
            boolean done = pool.shutdownGracefully(10);
            System.out.println("  优雅停机完成=" + done + " 最终拒绝数=" + pool.rejectedCount());
        } finally {
            // 无论演示哪个分支出错都要停掉池，否则非守护线程会让 JVM 无法退出
            pool.shutdownGracefully(5);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}