package com.study.concurrency.ratelimit;

/**
 * 熔断器：当下游持续失败达到阈值时快速失败（Open），冷却一段时间后放少量试探请求（Half-Open，
 * 半开），试探成功恢复（Closed），试探失败立刻再次熔断。
 *
 * <p>解决什么问题：没有熔断时，下游故障会让每个请求都干等超时，线程被占满、队列积压，
 * 故障向上游层层放大（雪崩）；熔断让「正在故障的下游」立刻被跳过，调用方收到快速失败，
 * 系统其余部分不受拖累，下游恢复后又能自动回归。
 *
 * <p>三态流转（面试必考）：
 * <pre>
 *   Closed ──连续失败≥阈值──▶ Open ──冷却时间到+试探通过──▶ Half-Open
 *   Half-Open ──试探失败──▶ Open    Half-Open ──试探成功──▶ Closed
 * </pre>
 *
 * <p>容易错的地方：① Half-Open 只放<b>少量</b>试探（并发试探过多等于没熔断）；
 *           ② Open 阶段必须快进快出（不允许请求进去干等），否则熔断形同虚设；
 *           ③ 成功要复位连续失败计数，否则故障恢复后计数残留、很快又熔断。
 */
public final class CircuitBreaker {

    /** 三态。 */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** 运行统计（监控面板/日志用）。 */
    public record Stats(long successCount, long failureCount, long fastFailCount) {
    }

    private final int failureThreshold;      // Closed 下连续失败多少次进入 Open
    private final long openDurationNanos;    // Open 持续多久后允许试探（冷却期）
    private final int halfOpenTrialLimit;    // Half-Open 阶段最多放行的试探请求数
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;         // Closed 下的连续失败计数
    private long openUntilNanos;             // 熔断到什么时候结束（Open 状态记录）
    private int halfOpenInFlight;            // Half-Open 下正在试探中的请求数
    private long successCount;
    private long failureCount;
    private long fastFailCount;

    /**
     * @param failureThreshold   连续失败达到该值进入 Open（如 5）
     * @param openDurationMillis Open 冷却时长（如 10_000）
     * @param halfOpenTrialLimit 半开试探并发上限（通常 1~3，试探过多等于没熔断）
     */
    public CircuitBreaker(int failureThreshold, long openDurationMillis, int halfOpenTrialLimit, Clock clock) {
        if (failureThreshold <= 0 || openDurationMillis <= 0 || halfOpenTrialLimit <= 0) {
            throw new IllegalArgumentException("三个参数都必须大于 0");
        }
        this.failureThreshold = failureThreshold;
        this.openDurationNanos = openDurationMillis * 1_000_000L;
        this.halfOpenTrialLimit = halfOpenTrialLimit;
        this.clock = clock;
    }

    public CircuitBreaker(int failureThreshold, long openDurationMillis, int halfOpenTrialLimit) {
        this(failureThreshold, openDurationMillis, halfOpenTrialLimit, Clock.system());
    }

    /**
     * 请求到来时判断「要不要放行」：放行返回 true，熔断快速失败返回 false。
     * 调用方放行后必须调用 {@link #onSuccess()} 或 {@link #onFailure()} 上报结果。
     */
    public synchronized boolean allowRequest() {
        long now = clock.nanos();
        switch (state) {
            case CLOSED -> {
                return true;
            }
            case OPEN -> {
                // 冷却时间已过：从 Open 进入 Half-Open，放行少量试探请求
                if (now >= openUntilNanos) {
                    state = State.HALF_OPEN;
                    halfOpenInFlight = 0;
                    halfOpenInFlight++;
                    return true;
                }
                fastFailCount++;
                return false;
            }
            case HALF_OPEN -> {
                // 半开阶段限制并发试探数：试探没回来之前不再放更多请求
                if (halfOpenInFlight < halfOpenTrialLimit) {
                    halfOpenInFlight++;
                    return true;
                }
                fastFailCount++;
                return false;
            }
            default -> throw new IllegalStateException("未知状态: " + state);
        }
    }

    /** 上报成功：Closed 复位失败计数；Half-Open 试探成功 → 恢复 Closed。 */
    public synchronized void onSuccess() {
        successCount++;
        switch (state) {
            case CLOSED -> consecutiveFailures = 0;
            case HALF_OPEN -> {
                halfOpenInFlight--;
                // 试探成功说明下游活了：全量开放
                state = State.CLOSED;
                consecutiveFailures = 0;
            }
            case OPEN -> {
                // 理论不会发生（Open 不允许放行），防御式处理：忽略并不动状态
            }
        }
    }

    /** 上报失败：Closed 累计连续失败，达到阈值进 Open；Half-Open 试探失败 → 立刻再次 Open。 */
    public synchronized void onFailure() {
        failureCount++;
        switch (state) {
            case CLOSED -> {
                consecutiveFailures++;
                if (consecutiveFailures >= failureThreshold) {
                    enterOpen();
                }
            }
            case HALF_OPEN -> {
                halfOpenInFlight--;
                // 试探失败：下游还没好，重新熔断，冷却期从头算
                enterOpen();
            }
            case OPEN -> {
                // 已熔断，忽略（快速失败的请求不算额外连续失败）
            }
        }
    }

    private void enterOpen() {
        state = State.OPEN;
        openUntilNanos = clock.nanos() + openDurationNanos;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized Stats stats() {
        return new Stats(successCount, failureCount, fastFailCount);
    }

    /**
     * 执行一个调用并自动上报成败：熔断期直接抛 {@link IllegalStateException}（快速失败），
     * 执行抛异常则记一次失败。适合「调用方不想手动配对 allowRequest/onSuccess」的场景。
     */
    public <T> T execute(java.util.function.Supplier<T> call) {
        if (!allowRequest()) {
            throw new IllegalStateException("熔断已打开，快速失败（不发起真实调用）");
        }
        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        }
    }

    /**
     * 演示：阈值 3、冷却 1.5 秒；先 3 次失败触发熔断 → 熔断期快速失败 → 等冷却后
     * 放行 1 个试探，试探成功则恢复 Closed。
     */
    public static void demo() throws InterruptedException {
        System.out.println("【熔断器演示】阈值=3 冷却=1.5 秒 半开试探=1");
        CircuitBreaker breaker = new CircuitBreaker(3, 1500, 1);
        // 阶段 1：连续 3 次失败 → Open
        for (int i = 1; i <= 3; i++) {
            try {
                breaker.execute(() -> {
                    throw new IllegalStateException("下游 500");
                });
            } catch (RuntimeException ignored) {
                // 预期失败
            }
            System.out.printf("  第 %d 次失败: 状态=%s%n", i, breaker.state());
        }
        // 阶段 2：熔断期快速失败
        for (int i = 1; i <= 2; i++) {
            boolean allowed = breaker.allowRequest();
            System.out.printf("  熔断期第 %d 个请求: %s%n", i, allowed ? "放行" : "快速失败");
        }
        // 阶段 3：等冷却后，试探成功
        Thread.sleep(1600);
        boolean first = breaker.allowRequest();
        System.out.println("  冷却后第 1 个请求: " + (first ? "放行(试探)" : "仍熔断"));
        breaker.onSuccess();
        System.out.println("  试探成功 → 状态=" + breaker.state() + "（已恢复 Closed）");
        boolean after = breaker.allowRequest();
        System.out.println("  恢复后请求: " + (after ? "正常放行" : "拒绝"));
        breaker.onSuccess();
        System.out.printf("  统计: 成功=%d 失败=%d 快速失败=%d%n",
                breaker.stats().successCount(), breaker.stats().failureCount(), breaker.stats().fastFailCount());
    }
}