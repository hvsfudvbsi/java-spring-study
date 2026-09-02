package com.study.concurrency.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.concurrency.testutil.FakeClock;

class LeakyBucketTest {

    @Test
    @DisplayName("桶容量: 容量 3 时第 4 个请求被丢弃")
    void capacityDropsOverflow() {
        LeakyBucket bucket = new LeakyBucket(3, 1, FakeClock.startingAtMillis(0));
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "桶满，第 4 个被丢弃");
    }

    @Test
    @DisplayName("懒排水: 时间过去后按速率漏出水位，漏出多少就能再进多少")
    void drainByElapsedTime() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        LeakyBucket bucket = new LeakyBucket(3, 2, clock); // 输出 2/秒
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "水位 3/3");

        clock.advanceMillis(1000); // 1 秒漏 2 个
        assertEquals(1.0, bucket.waterLevel(), 1e-6);
        assertTrue(bucket.tryAcquire(), "漏出 2 个可再进 2 个");
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "又满了");
    }

    @Test
    @DisplayName("长时间空闲: 封顶 3，水位不会减成负数，仍只允许 3 个突发")
    void idleDoesNotCreateNegativeWater() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        LeakyBucket bucket = new LeakyBucket(3, 5, clock);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        clock.advanceMillis(60_000); // 空闲 1 分钟
        assertEquals(0.0, bucket.waterLevel(), 1e-6, "长时间空闲后水位清零不可能是负数");
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "水量封顶仍是容量 3");
    }

    @Test
    @DisplayName("非法参数: 容量或速率非正数时拒绝创建")
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(0, 1, FakeClock.startingAtMillis(0)));
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(1, 0, FakeClock.startingAtMillis(0)));
    }
}