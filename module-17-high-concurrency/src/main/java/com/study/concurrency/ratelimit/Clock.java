package com.study.concurrency.ratelimit;

/**
 * 可注入的时间源：生产用 {@link System#nanoTime()}，测试可注入假时钟「快进」，
 * 让限流/熔断的时间逻辑变成确定性断言，而不是真的等 1 秒。
 *
 * <p>为什么用 nanoTime 而不是 currentTimeMillis：nanoTime 单调递增、不受系统改时间影响，
 * 适合测量间隔；限流器只关心「过去了多久」，不关心绝对时间。
 */
@FunctionalInterface
public interface Clock {

    /** 当前纳秒时间（单调递增）。 */
    long nanos();

    /** 默认实现：直接用系统纳秒钟。 */
    static Clock system() {
        return System::nanoTime;
    }
}