package com.study.concurrency.ratelimit;

/**
 * 令牌桶限流器：按固定速率向桶里放令牌，请求来了拿走 N 个令牌，不够就拒绝（或等待）。
 *
 * <p>特点与适用场景：允许一定程度的<b>突发</b>——桶容量就是可积累的「突发额度」，
 * 空闲期攒下的令牌可以瞬间放走一大批请求（例如秒杀刚开始的集中放量）。
 * 典型实现是「懒计算」：不真的起定时器发令牌，而是在每次请求来时按经过的时间补发，
 * 省掉一个后台线程，误差在可接受范围。
 *
 * <p>容易错的地方：① 补发要「惰性 + 封顶」，长时间空闲后一次性补到满桶而不是无限累积；
 *           ② 多线程并发取令牌必须加锁（或 CAS），否则同一批令牌被多个线程同时取走；
 *           ③ 放行后要扣掉令牌，否则限流失效。
 *
 * <p>运行入口：{@link #demo()}（真实限速 2 次/秒，配合 5 次快速调用观察拒绝）。
 */
public final class TokenBucket {

    private final double capacity;        // 桶容量：瞬间最多可放行的令牌数（突发额度）
    private final double refillPerNano;   // 补发速率：个/纳秒 = ratePerSecond / 1e9
    private final Clock clock;
    private double tokens;                // 当前令牌数（懒计算）
    private long lastRefillNanos;         // 上次补发时间

    /**
     * @param capacity       桶容量（最大突发令牌数，>0）
     * @param ratePerSecond  持续速率（令牌/秒）
     * @param initiallyFull  初始是否满桶；false 表示从 0 开始，头几个请求需要等补发
     */
    public TokenBucket(double capacity, double ratePerSecond, boolean initiallyFull, Clock clock) {
        if (capacity <= 0 || ratePerSecond <= 0) {
            throw new IllegalArgumentException("capacity 与 ratePerSecond 必须大于 0");
        }
        this.capacity = capacity;
        this.refillPerNano = ratePerSecond / 1_000_000_000.0;
        this.tokens = initiallyFull ? capacity : 0;
        this.lastRefillNanos = clock.nanos();
        this.clock = clock;
    }

    public TokenBucket(double capacity, double ratePerSecond) {
        this(capacity, ratePerSecond, true, Clock.system());
    }

    /**
     * 尝试拿走 n 个令牌：够就放行并返回 true，不够返回 false（不等待，立即失败）。
     * 调用前先补发令牌，保证「时间过了就该有令牌」。
     */
    public synchronized boolean tryAcquire(int n) {
        refill();
        if (tokens >= n) {
            tokens -= n;
            return true;
        }
        return false;
    }

    /**
     * 阻塞式获取：最多等 timeoutMillis 毫秒；等到令牌返回 true，超时返回 false。
     * 内部用 wait/notifyAll：有令牌释放时唤醒等待者（演示用，生产通常用锁+条件变量）。
     */
    public synchronized boolean acquire(int n, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (!tryAcquire(n)) {
            long remain = deadline - System.nanoTime();
            if (remain <= 0) {
                return false;
            }
            wait(Math.min(remain / 1_000_000L, 10) + 1); // 最多每 10ms 醒来重试一次
        }
        return true;
    }

    /** 懒补发：按「上次补发到现在」的时间乘以速率补令牌，并封顶到容量。 */
    private void refill() {
        long now = clock.nanos();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
        lastRefillNanos = now;
    }

    /** 当前累积令牌数（调试用）。 */
    public synchronized double availableTokens() {
        refill();
        return tokens;
    }

    /**
     * 演示：桶 2 个令牌、速率 2/秒；连发 5 个请求，前 2 个因满桶放行触发突发，
     * 之后的被拒绝（速率还没补上来），等 1 秒后再试能放行 2 个。
     */
    public static void demo() throws InterruptedException {
        System.out.println("【令牌桶演示】容量=2 速率=2/秒：先看突发放行，再看限速生效");
        TokenBucket bucket = new TokenBucket(2, 2);
        long start = System.nanoTime();
        int pass = 0;
        for (int i = 1; i <= 5; i++) {
            boolean ok = bucket.tryAcquire(1);
            System.out.printf("  第 %d 个请求: %s（累计 %.2f 秒）%n", i, ok ? "放行" : "拒绝",
                    (System.nanoTime() - start) / 1_000_000_000.0);
            if (ok) {
                pass++;
            }
        }
        System.out.println("  前 5 个请求放行 " + pass + " 个（0 秒时满桶 2 个 + 瞬时补发不足 → 只放行 2 个）");
        Thread.sleep(1100); // 等 1 秒，速率 2/秒 应补回约 2 个令牌
        int later = 0;
        for (int i = 1; i <= 3; i++) {
            if (bucket.tryAcquire(1)) {
                later++;
            }
        }
        System.out.println("  等 1.1 秒后连发 3 个，放行 " + later + " 个（约等于补发的令牌数）");
    }
}