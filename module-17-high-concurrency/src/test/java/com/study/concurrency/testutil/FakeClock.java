package com.study.concurrency.testutil;

import com.study.concurrency.ratelimit.Clock;

/**
 * 测试用假时钟：时间由测试手动拨动，让限流/熔断的时间逻辑变成确定性断言，
 * 不用真的 sleep 等真实秒数。
 */
public final class FakeClock implements Clock {

    private long now;

    public FakeClock(long startNanos) {
        this.now = startNanos;
    }

    public static FakeClock startingAtMillis(long startMillis) {
        return new FakeClock(startMillis * 1_000_000L);
    }

    public void advanceMillis(long millis) {
        now += millis * 1_000_000L;
    }

    public void advanceNanos(long nanos) {
        now += nanos;
    }

    @Override
    public long nanos() {
        return now;
    }
}