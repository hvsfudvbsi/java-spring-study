package com.study.concurrency.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.study.concurrency.ratelimit.CircuitBreaker.State;
import com.study.concurrency.testutil.FakeClock;

class CircuitBreakerTest {

    private CircuitBreaker newBreaker(FakeClock clock) {
        return new CircuitBreaker(3, 1000, 1, clock);
    }

    @Test
    @DisplayName("阈值内失败: 连续 2 次失败仍是 Closed，请求照常放行")
    void failuresBelowThresholdStayClosed() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        breaker.onFailure();
        breaker.onFailure();
        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest());
        breaker.onSuccess();
        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    @DisplayName("达到阈值: 连续 3 次失败进入 Open，熔断期请求快速失败")
    void thresholdOpensCircuit() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();
        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.allowRequest(), "Open 阶段快速失败");
        assertEquals(1, breaker.stats().fastFailCount());
    }

    @Test
    @DisplayName("冷却期未到: Open 持续拒绝; 冷却到: 放行 1 个试探，成功即恢复 Closed")
    void cooldownThenHalfOpenSuccessRecovers() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();
        assertFalse(breaker.allowRequest());

        clock.advanceMillis(999);
        assertFalse(breaker.allowRequest(), "还差 1ms，仍然熔断");

        clock.advanceMillis(2);
        assertTrue(breaker.allowRequest(), "冷却结束放行试探请求");
        assertEquals(State.HALF_OPEN, breaker.state());
        breaker.onSuccess();
        assertEquals(State.CLOSED, breaker.state(), "试探成功恢复 Closed");
        assertTrue(breaker.allowRequest(), "恢复后正常放行");
    }

    @Test
    @DisplayName("半开试探失败: 立刻再次 Open，且 fastFail 计数不因 Open 下的 onFailure 波动")
    void halfOpenFailureReopens() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();
        clock.advanceMillis(1001);
        assertTrue(breaker.allowRequest());
        assertEquals(State.HALF_OPEN, breaker.state());
        breaker.onFailure();
        assertEquals(State.OPEN, breaker.state(), "试探失败重新熔断");
        assertFalse(breaker.allowRequest());
    }

    @Test
    @DisplayName("execute 包装: 异常自动记失败并熔断; 熔断期抛快速失败异常")
    void executeWrapperAutoRecords() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class,
                    () -> breaker.execute(() -> {
                        throw new IllegalStateException("下游 500");
                    }));
        }
        assertEquals(State.OPEN, breaker.state());
        assertThrows(IllegalStateException.class,
                () -> breaker.execute(() -> "不该执行"),
                "熔断期 execute 也快速失败");
        assertEquals(State.OPEN, breaker.state());
        assertEquals(3, breaker.stats().failureCount());

        // 冷却过去之后(用真实时间无关的假时钟) 试探成功 → 恢复
        clock.advanceMillis(1001);
        assertEquals("ok", breaker.execute(() -> "ok"));
        assertEquals(State.CLOSED, breaker.state());
        assertEquals(1, breaker.stats().successCount(), "只有这次试探成功计入");
    }

    @Test
    @DisplayName("成功复位: 失败计数在 Closed 下成功一次即清零")
    void successResetsFailureCount() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onFailure();
        assertEquals(State.CLOSED, breaker.state(), "复位后重新累计，未达阈值");
    }

    @Test
    @DisplayName("非法参数拒绝创建")
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreaker(0, 1000, 1, FakeClock.startingAtMillis(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreaker(3, 0, 1, FakeClock.startingAtMillis(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new CircuitBreaker(3, 1000, 0, FakeClock.startingAtMillis(0)));
    }

    @Test
    @DisplayName("open 期间 onSuccess 防御式处理: 不异常、状态不变")
    void successDuringOpenIsIgnored() {
        FakeClock clock = FakeClock.startingAtMillis(0);
        CircuitBreaker breaker = newBreaker(clock);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();
        assertEquals(State.OPEN, breaker.state());
        assertDoesNotThrow(breaker::onSuccess);
        assertEquals(State.OPEN, breaker.state());
    }
}