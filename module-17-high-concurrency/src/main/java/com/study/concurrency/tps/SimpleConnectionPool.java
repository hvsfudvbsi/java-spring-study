package com.study.concurrency.tps;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用连接池（最小实现）：复用昂贵资源（数据库连接 / HTTP 客户端 / 消息生产者），避免「每请求新建」。
 *
 * <p>为什么省：创建连接的成本很高（TCP 握手、TLS 协商、认证建会话，动辄几十毫秒），
 * 高并发下「每请求建连接」会打满 CPU 在握手上、还可能耗尽系统的文件描述符/端口；
 * 连接池只维护少量连接，请求从池里借还，把创建成本摊到上万次请求上。
 *
 * <p>实现要点：{@link Semaphore} 限制同时外借的总数（超借就在 acquire 上等）+
 * 空闲连接 Deque 供复用；没有空闲连接且还有配额时才新建（保证连接数不超过 maxSize）。
 *
 * <p>学习目标：borrow/return 的租赁语义、acquire 超时（拿不到连接不无限等）、
 * 归还时把异常连接废弃而不是放回池子（{@code Lease#release(boolean)}）——这是生产连接池
 * 校验连接的通用套路。
 *
 * @param <T> 池化资源类型
 */
public final class SimpleConnectionPool<T> implements AutoCloseable {

    /** 一次「创建连接」的模拟开销（毫秒）：真实场景是 TCP/TLS/认证，这里用睡眠代替。 */
    private static final long CREATE_COST_MILLIS = 2;

    /** 池租赁句柄：AutoCloseable，配合 try-with-resources 保证「借了必还」。 */
    public final class Lease implements AutoCloseable {
        private final T resource;
        private boolean broken;

        private Lease(T resource) {
            this.resource = resource;
        }

        public T get() {
            return resource;
        }

        /** 连接用坏了调用它：归还时直接废弃而不是放回池子。 */
        public void markBroken() {
            this.broken = true;
        }

        @Override
        public void close() {
            // 借出期间出现未捕获异常时，调用方可能忘记标记 broken；
            // 稳妥做法是默认尝试归还（演示简化），真实实现通常配合 try/catch 标记 + 校验归还
            release(resource, broken);
        }
    }

    private final java.util.function.Supplier<T> factory;
    private final Semaphore permits;          // 剩余可外借额度：maxSize - 已外借
    private final int maxSize;                // 池上限（供 stats 反推活跃数）
    private final Deque<T> idle = new ArrayDeque<>(); // 空闲连接（复用来源），只被 acquire/release 的锁保护
    private final Object lock = new Object();
    private final AtomicInteger created = new AtomicInteger(); // 累计创建数（监控/压测指标）
    private final AtomicInteger reused = new AtomicInteger();  // 累计复用数
    private final AtomicInteger waitCount = new AtomicInteger(); // 累计等待次数
    private volatile boolean closed;

    /**
     * @param factory  创建新连接的工厂（真实项目是 DriverManager.getConnection 等）
     * @param maxSize  池上限：同时外借的连接数不超过它；超过的请求在 acquire 上等待
     */
    public SimpleConnectionPool(java.util.function.Supplier<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.permits = new Semaphore(maxSize);
    }

    /**
     * 借连接：优先复用空闲，没有空闲且未超上限则新建；池满则最多等待 timeoutMillis 后放弃。
     *
     * @param timeoutMillis 最长等待时间；0 表示不等待（拿不到立刻失败）
     * @return 租赁句柄（用后必须 close）
     * @throws java.util.concurrent.TimeoutException 等待超时仍无空闲连接
     */
    public Lease acquire(long timeoutMillis) throws Exception {
        if (closed) {
            throw new IllegalStateException("连接池已关闭");
        }
        if (!permits.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
            waitCount.incrementAndGet();
            throw new java.util.concurrent.TimeoutException("等待空闲连接超时: " + timeoutMillis + "ms");
        }
        try {
            T pooled = takeIdle();
            if (pooled != null) {
                reused.incrementAndGet();
                return new Lease(pooled);
            }
            // 到这里说明「有空闲额度但没有空闲连接」→ 需要新建（额度已在 acquire 时扣掉）
            long t0 = System.nanoTime();
            T fresh = factory.get();
            created.incrementAndGet();
            return new Lease(fresh);
        } catch (Exception e) {
            // 创建失败要还额度，否则额度会被泄漏、池子越借越少
            permits.release();
            throw e;
        }
    }

    private T takeIdle() {
        synchronized (lock) {
            return idle.pollFirst();
        }
    }

    private void release(T resource, boolean broken) {
        if (broken) {
            // 坏连接直接丢弃：放回池子会让「看似可用实则已断」的连接坑到下一个请求
            closeSilently(resource);
        } else {
            synchronized (lock) {
                idle.addLast(resource);
            }
        }
        // 归还额度 → 正在等待的 acquire 得以继续
        permits.release();
    }

    private static void closeSilently(Object resource) {
        if (resource instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception ignored) {
                // 关闭失败无需外抛，池子本来就要丢弃它
            }
        }
    }

    /** 池统计（压测/监控用）。 */
    public record Stats(int created, int reused, int waitCount, int idleCount, int activeCount) {
        /** 复用率：从池里拿到的连接中，多少是复用的（越高说明池越省）。 */
        public double reuseRatio() {
            int total = created + reused;
            return total == 0 ? 0 : reused / (double) total;
        }
    }

    public Stats stats() {
        // 活跃(已外借) = 总配额 - 剩余许可；与空闲数无关（空闲也是「未外借」）
        int active = maxSize - permits.availablePermits();
        synchronized (lock) {
            return new Stats(created.get(), reused.get(), waitCount.get(),
                    idle.size(), Math.max(0, active));
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (lock) {
            idle.forEach(SimpleConnectionPool::closeSilently);
            idle.clear();
        }
    }

    /** 模拟「昂贵连接」的包装：创建需要 CREATE_COST_MILLIS 毫秒（真实场景是握手+认证）。 */
    public static final class ExpensiveHandle implements AutoCloseable {
        private static final AtomicInteger SEQ = new AtomicInteger();
        private final int id = SEQ.incrementAndGet();

        ExpensiveHandle() {
            try {
                Thread.sleep(CREATE_COST_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public int id() {
            return id;
        }

        @Override
        public void close() {
            // 真实连接关闭时释放 socket/文件句柄；演示无资源，留空即可
        }
    }

    /** 演示：对比「每任务新建连接」与「连接池复用」，看创建次数与复用率。 */
    public static void demo() throws Exception {
        int tasks = 20;
        int poolSize = 3;
        System.out.println("【连接池演示】20 个任务并发，池上限 " + poolSize + "（创建 1 个连接约 2ms）");

        // 没有池：每个任务都 new 一次（真实场景是每次请求都握手建连）
        long t0 = System.nanoTime();
        int noPoolTotal = 0;
        try (var pool = new SimpleConnectionPool<ExpensiveHandle>(ExpensiveHandle::new, tasks)) {
            for (int i = 0; i < tasks; i++) {
                try (var lease = pool.acquire(0)) {
                    noPoolTotal++; // 模拟用连接做了一次操作
                }
            }
        }
        long t1 = System.nanoTime();

        // 有池：只创建 3 个连接，其余全部复用
        long t2 = System.nanoTime();
        int poolTotal = 0;
        SimpleConnectionPool<ExpensiveHandle> pool = new SimpleConnectionPool<>(ExpensiveHandle::new, poolSize);
        try (pool) {
            for (int i = 0; i < tasks; i++) {
                try (var lease = pool.acquire(10_000)) {
                    poolTotal++;
                }
            }
        }
        long t3 = System.nanoTime();
        Stats pooledStats = pool.stats();

        System.out.printf("  无池: %.1f ms（创建 %d 次）%n", (t1 - t0) / 1_000_000.0, tasks);
        System.out.printf("  有池: %.1f ms（创建 %d 次）%n", (t3 - t2) / 1_000_000.0, pooledStats.created());
        // 注意：printf 的 %f 只接受浮点，long 除 long 要先把除数变成 double，否则抛 IllegalFormatConversionException
        System.out.printf("  有池快 %.1f 倍，任务都成功=%b%n",
                (t1 - t0) / (double) Math.max(t3 - t2, 1L), noPoolTotal == tasks && poolTotal == tasks);
    }
}