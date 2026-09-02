package com.study.concurrency.ratelimit;

/**
 * 漏桶限流器：请求先进桶排队，桶底部按固定速率「漏出」（放行），桶满了新请求直接丢弃。
 *
 * <p>与令牌桶的本质区别：令牌桶允许突发（攒的令牌可以瞬间花光），漏桶<b>输出速率恒定</b>——
 * 流量再猛，出桶也是匀速的，天然平滑了尖峰。适用场景：下游是脆弱系统（短信渠道、
 * 老数据库）时，可以确保对它永远是平稳的请求节奏；令牌桶则适合「允许适度突发」的场景。
 *
 * <p>实现同样是懒计算：不启动排水线程，每次来请求时按经过时间计算「漏掉了多少」，
 * 再决定能否进桶。water 就是当前桶内积压的请求数。
 *
 * <p>容易错的地方：① 漏水的量要按「距上次计算过了多久」算，不能每次都只漏固定 1 ；
 *           ② water 不能减成负数；③ 水位封顶在 capacity，溢出即丢弃。
 */
public final class LeakyBucket {

    private final double capacity;      // 桶容量：桶内最多积压的请求数（积压缓冲）
    private final double drainPerNano;  // 排水速率：个/纳秒 = ratePerSecond / 1e9
    private final Clock clock;
    private double water;               // 当前桶内积压
    private long lastDrainNanos;        // 上次排水计算时间

    /**
     * @param capacity      桶容量（可积压的请求数）
     * @param ratePerSecond 恒定输出速率（请求/秒）
     */
    public LeakyBucket(double capacity, double ratePerSecond, Clock clock) {
        if (capacity <= 0 || ratePerSecond <= 0) {
            throw new IllegalArgumentException("capacity 与 ratePerSecond 必须大于 0");
        }
        this.capacity = capacity;
        this.drainPerNano = ratePerSecond / 1_000_000_000.0;
        this.water = 0;
        this.lastDrainNanos = clock.nanos();
        this.clock = clock;
    }

    public LeakyBucket(double capacity, double ratePerSecond) {
        this(capacity, ratePerSecond, Clock.system());
    }

    /**
     * 尝试进桶：先漏掉这段时间该漏的水，再判断能不能把当前请求装进桶。
     * 返回 true 表示「进桶成功，稍后会被匀速放行」。
     */
    public synchronized boolean tryAcquire() {
        drain();
        if (water + 1 <= capacity) {
            water += 1;
            return true;
        }
        return false; // 桶满，请求被丢弃（真实系统这里通常是快速失败或丢进日志）
    }

    /** 懒排水：按经过的时间 × 速率计算漏掉多少。 */
    private void drain() {
        long now = clock.nanos();
        long elapsed = now - lastDrainNanos;
        if (elapsed <= 0) {
            return;
        }
        water = Math.max(0, water - elapsed * drainPerNano);
        lastDrainNanos = now;
    }

    /** 当前桶内积压量（调试用）。 */
    public synchronized double waterLevel() {
        drain();
        return water;
    }

    /** 演示：容量 3、输出 2/秒；瞬间连发 5 个只进 3 个（桶满丢弃），等 1 秒后又能进 2 个。 */
    public static void demo() throws InterruptedException {
        System.out.println("【漏桶演示】容量=3 输出=2/秒：尖峰被削平，桶满多余请求被丢弃");
        LeakyBucket bucket = new LeakyBucket(3, 2);
        int accepted = 0;
        for (int i = 1; i <= 5; i++) {
            boolean ok = bucket.tryAcquire();
            System.out.printf("  第 %d 个请求: %s（水位 %.1f）%n", i, ok ? "进桶" : "丢弃", bucket.waterLevel());
            if (ok) {
                accepted++;
            }
        }
        System.out.println("  瞬间连发 5 个，进桶 " + accepted + " 个（容量 3，第 4/5 个被丢弃）");
        Thread.sleep(1100); // 等 1 秒：2/秒 输出应漏掉约 2 个水位
        int later = 0;
        for (int i = 1; i <= 3; i++) {
            if (bucket.tryAcquire()) {
                later++;
            }
        }
        System.out.println("  等 1.1 秒后再发 3 个，进桶 " + later + " 个（约等于漏出的水位）");
    }
}