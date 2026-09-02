package com.study.concurrency.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 滑动窗口计数器限流器：记录每个请求的时间戳，只统计「当前窗口内（最近 N 毫秒）」的请求数。
 *
 * <p>与固定窗口的区别：固定窗口（如每分钟最多 100 次）在窗口边界处有「双倍突发」漏洞——
 * 59 秒时发了 100 个，下一秒又发 100 个，瞬间 200 个请求紧挨着；滑动窗口以「最近 1 分钟」
 * 为窗口，每过一点时间窗口就滑动一点，不会跳变，边界处的洪峰被打平。
 *
 * <p>本实现是<b>精确滑动窗口</b>（逐个时间戳记录），请求非常密集时时间戳列表长得快；
 * 生产上常用「分桶近似」版本（把窗口切成 K 个小区间只算桶计数）来省内存。
 *
 * <p>容易错的地方：统计前必须先剔除窗口外的旧时间戳（否则计数虚高）；多线程并发访问
 * 列表要加锁或使用并发容器；时钟回拨会让「剔除」逻辑误删新请求（生产用单调时钟规避）。
 */
public final class SlidingWindowCounter {

    private final int maxRequests;      // 窗口内最多放行的请求数
    private final long windowNanos;     // 窗口时长（纳秒）
    private final Clock clock;
    private final Deque<Long> timestamps = new ArrayDeque<>(); // 已放行请求的时间戳（有序）

    /**
     * @param maxRequests    窗口内允许的最大请求数
     * @param windowMillis   窗口时长（毫秒）
     */
    public SlidingWindowCounter(int maxRequests, long windowMillis, Clock clock) {
        if (maxRequests <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("maxRequests 与 windowMillis 必须大于 0");
        }
        this.maxRequests = maxRequests;
        this.windowNanos = windowMillis * 1_000_000L;
        this.clock = clock;
    }

    public SlidingWindowCounter(int maxRequests, long windowMillis) {
        this(maxRequests, windowMillis, Clock.system());
    }

    /**
     * 尝试放行：先清掉窗口外的旧时间戳，若窗口内计数未满则记录本次并放行。
     * 返回 true 表示放行，false 表示窗口已满（限流）。
     */
    public synchronized boolean tryAcquire() {
        long now = clock.nanos();
        // 窗口 = [now - windowNanos, now)：比窗口起点还旧的请求「过期」，从队头弹出
        long cutoff = now - windowNanos;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= maxRequests) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    /** 当前窗口内已放行数（调试用）。 */
    public synchronized int currentCount() {
        long now = clock.nanos();
        long cutoff = now - windowNanos;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.removeFirst();
        }
        return timestamps.size();
    }

    /** 演示：窗口 1 秒内最多 3 个；瞬间发 5 个只过 3 个，等 500ms 后因为窗口滑动又能过 1 个。 */
    public static void demo() throws InterruptedException {
        System.out.println("【滑动窗口演示】1 秒窗口最多 3 个请求");
        SlidingWindowCounter counter = new SlidingWindowCounter(3, 1000);
        int pass = 0;
        long start = System.nanoTime();
        for (int i = 1; i <= 5; i++) {
            boolean ok = counter.tryAcquire();
            System.out.printf("  第 %d 个请求: %s（当前窗口计数 %d）%n", i, ok ? "放行" : "拒绝",
                    counter.currentCount());
            if (ok) {
                pass++;
            }
        }
        System.out.println("  瞬间连发 5 个，放行 " + pass + " 个（窗口上限 3）");
        Thread.sleep(1100); // 等窗口滑过第一个请求
        boolean later = counter.tryAcquire();
        System.out.println("  等 1.1 秒后再发 1 个: " + (later ? "放行" : "拒绝")
                + "（最早的时间戳已滑出窗口，窗口空出位置）");
    }
}