package com.study.concurrency.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.concurrency.testutil.FakeClock;

class SlidingWindowCounterTest {

    @Test
    @DisplayName("窗口上限: 1 秒窗口最多 3 个，第 4 个被拒绝")
    void windowLimitEnforced() {
        SlidingWindowCounter counter = new SlidingWindowCounter(3, 1000, FakeClock.startingAtMillis(0));
        assertTrue(counter.tryAcquire());
        assertTrue(counter.tryAcquire());
        assertTrue(counter.tryAcquire());
        assertFalse(counter.tryAcquire(), "窗口计数已满");
        assertEquals(3, counter.currentCount());
    }

    @Test
    @DisplayName("窗口未滑过: 只过一半时间，旧请求仍占窗口，继续拒绝")
    void windowNotYetSlid() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        SlidingWindowCounter counter = new SlidingWindowCounter(3, 1000, clock);
        counter.tryAcquire();
        counter.tryAcquire();
        counter.tryAcquire();
        clock.advanceMillis(500);
        assertFalse(counter.tryAcquire(), "500ms 后最早的请求仍在 1 秒窗口内");
    }

    @Test
    @DisplayName("窗口滑动: 满 1 秒后旧请求过期，重新放行")
    void windowSlidesAfterFullDuration() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        SlidingWindowCounter counter = new SlidingWindowCounter(3, 1000, clock);
        counter.tryAcquire();
        counter.tryAcquire();
        counter.tryAcquire();
        clock.advanceMillis(1001);
        assertTrue(counter.tryAcquire(), "阈值 0 毫秒前的请求已滑出窗口");
        assertEquals(1, counter.currentCount(), "窗口内只剩新放的 1 个");
    }

    @Test
    @DisplayName("部分过期: 2 个过期后窗口空出 2 个位置")
    void partialSliding() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        SlidingWindowCounter counter = new SlidingWindowCounter(3, 1000, clock);
        counter.tryAcquire();
        counter.tryAcquire(); // 两个旧请求
        clock.advanceMillis(1200);
        assertTrue(counter.tryAcquire());
        assertTrue(counter.tryAcquire());
        assertTrue(counter.tryAcquire(), "旧计清零后容量完全空出 3 个位置");
        assertFalse(counter.tryAcquire(), "窗口再次计数满");
    }

    @Test
    @DisplayName("非法参数: 上限或窗口非正数时拒绝创建")
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowCounter(0, 1000, FakeClock.startingAtMillis(0)));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowCounter(1, 0, FakeClock.startingAtMillis(0)));
    }
}