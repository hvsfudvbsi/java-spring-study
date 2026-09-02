package com.study.concurrency.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.concurrency.testutil.FakeClock;

class TokenBucketTest {

    @Test
    @DisplayName("满桶: 容量内一次性拿走成功，超容量立刻失败")
    void fullBucketAllowsBurst() {
        TokenBucket bucket = new TokenBucket(5, 2, true, FakeClock.startingAtMillis(0));
        assertTrue(bucket.tryAcquire(5), "满桶 5 个令牌应一次放行");
        assertFalse(bucket.tryAcquire(1), "令牌已扣光，立即失败");
    }

    @Test
    @DisplayName("懒补发: 时间过去后按速率补齐令牌，补多少能取多少")
    void refillByElapsedTime() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        // 每秒补 1 个，初始空桶
        TokenBucket bucket = new TokenBucket(10, 1, false, clock);
        assertFalse(bucket.tryAcquire(1), "初始 0 令牌");

        clock.advanceMillis(2000); // 2 秒 = 2 个令牌
        assertTrue(bucket.tryAcquire(2), "攒够 2 个一次拿走");
        assertFalse(bucket.tryAcquire(1), "剩余 0，再取失败");

        clock.advanceMillis(500); // 再 0.5 秒 = 0.5 个令牌
        assertEquals(0.5, bucket.availableTokens(), 1e-6);
        assertFalse(bucket.tryAcquire(1), "0.5 个不足以取 1 个");

        clock.advanceMillis(500); // 凑满 1 个令牌
        assertTrue(bucket.tryAcquire(1), "累计 1 个令牌可取");
    }

    @Test
    @DisplayName("封顶: 空闲再久也只能攒到容量，不会无限累积")
    void refillCappedAtCapacity() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        TokenBucket bucket = new TokenBucket(2, 100, false, clock);
        clock.advanceMillis(60_000); // 空闲 1 分钟，速率 100/秒
        assertEquals(2.0, bucket.availableTokens(), 1e-6, "封顶到容量 2 而非 6000");
        assertTrue(bucket.tryAcquire(2), "取走封顶的 2 个");
        assertFalse(bucket.tryAcquire(2), "再取失败");
    }

    @Test
    @DisplayName("阻塞式获取: 等令牌放行后返回 true（真实时钟，令牌 100ms 内补上）")
    void blockingAcquireWaitsForRefill() throws InterruptedException {
        // 初始空桶，速率 20/秒 → 100ms 补 1 个；200ms 超时足够拿到
        TokenBucket bucket = new TokenBucket(5, 20, false, Clock.system());
        assertTrue(bucket.acquire(1, 2000), "等待补发后应放行");
    }

    @Test
    @DisplayName("非法参数: 容量或速率非正数时拒绝创建")
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1, true, FakeClock.startingAtMillis(0)));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0, true, FakeClock.startingAtMillis(0)));
    }
}