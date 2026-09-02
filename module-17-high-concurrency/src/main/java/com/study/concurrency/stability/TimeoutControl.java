package com.study.concurrency.stability;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 超时控制演示：外部调用（RPC / HTTP / 数据库）必须设超时，否则一个慢调用会无限占着线程。
 *
 * <p>问题背景：调用下游不设超时 = 线程被无限期占用，请求多时线程池被打满，
 * 新请求要么排队要么丢弃，故障透过「线程饿死」传导到自己系统——这是高并发场景最常见的
 * 无意识雪崩源。正确做法：Future.get 带超时 + 超时后 cancel(true) 把底层调用中断掉。
 *
 * <p>两种实现对照：
 * <ul>
 *   <li>传统：线程池 + {@code Future#get(timeout)}，超时后 {@code cancel(true)}。</li>
 *   <li>Java 21 虚拟线程：每个调用开一个廉价的虚拟线程（创建成本低、内存占用小，
 *       可以「一次调用一个线程」而不怕线程数爆炸），同样用 Future + 超时取消收尾。</li>
 * </ul>
 *
 * <p>容易错的地方：① 超时后必须 cancel，否则调用还在后台悄悄跑（占线程/资源）；
 *           ② cancel(true) 只保证发出中断，任务若忽略中断仍在跑——所以真实 I/O 要选
 *           可中断的实现（socket.setSoTimeout、HTTP 客户端的读超时等）；
 *           ③ 超时时间要按「调用自身」设置，别只设在外层网关。
 */
public final class TimeoutControl {

    private TimeoutControl() {
    }

    /**
     * 传统线程池超时调用：超时抛 {@link TimeoutException}（已 cancel 底层任务）。
     *
     * @param pool        线程池（由调用方管理生命周期）
     * @param call        真实业务调用（应使用可中断的 I/O 实现）
     * @param timeoutMillis 超时毫秒
     */
    public static <T> T callWithTimeout(ExecutorService pool, Callable<T> call, long timeoutMillis)
            throws Exception {
        Future<T> future = pool.submit(call);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 关键一步：超时后立刻取消，并向执行线程发出中断（任务若响应中断会停止工作）
            future.cancel(true);
            throw e;
        }
    }

    /**
     * 虚拟线程版：为每个调用创建独立虚拟线程，天然隔离、超时取消互不影响。
     * Java 21 的虚拟线程挂起时几乎不占系统资源，可以「每个请求一个线程」。
     */
    public static <T> T callWithTimeoutVirtual(Callable<T> call, long timeoutMillis) throws Exception {
        // 每个调用都开一个虚拟线程执行，Future 用于统一收口结果/超时/取消
        var executor = Executors.newSingleThreadExecutor(virtualThreadFactory());
        try {
            Future<T> future = executor.submit(call);
            try {
                return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } finally {
            // 虚拟线程池也必须 shutdown，否则底层线程不回收；这里用 shutdownNow 兜底
            executor.shutdownNow();
        }
    }

    /** 虚拟线程工厂：线程名带 virtual- 前缀便于区分（jstack 里 virtual 线程有标记）。 */
    public static ThreadFactory virtualThreadFactory() {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> Thread.ofVirtual().name("virtual-call-" + seq.incrementAndGet())
                .unstarted(runnable);
    }

    /** 演示：正常调用 vs 超时调用（任务响应中断并记录被取消）。 */
    public static void demo() {
        System.out.println("【超时控制演示】调用方设 200ms 超时；任务慢 500ms 应该被取消");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 正常调用：任务 100ms < 超时 200ms
            String ok = callWithTimeout(pool, () -> {
                Thread.sleep(100);
                return "正常结果";
            }, 200);
            System.out.println("  正常调用: " + ok);

            // 慢调用：任务 800ms > 超时 200ms，应抛 TimeoutException
            java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                callWithTimeout(pool, () -> {
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException e) {
                        // 任务响应了 cancel(true) 发来的中断——证明底层真的被停掉了
                        cancelled.set(true);
                        Thread.currentThread().interrupt();
                    }
                    return "不该返回";
                }, 200);
                System.out.println("  ⚠ 未超时（异常路径，不该走到）");
            } catch (TimeoutException e) {
                System.out.println("  慢调用: 抛 TimeoutException（200ms 即返回，调用方线程没有被占住）");
            }
            System.out.println("  慢任务是否收到中断被取消=" + cancelled.get()
                    + "（true 说明 cancel(true) 真实生效）");

            // 虚拟线程版
            String v = callWithTimeoutVirtual(() -> {
                Thread.sleep(50);
                return "虚拟线程结果";
            }, 200);
            System.out.println("  虚拟线程调用: " + v + "（线程名=" + Thread.currentThread().getName() + "）");
        } catch (Exception e) {
            System.out.println("  演示异常: " + e);
        } finally {
            pool.shutdownNow();
        }
    }
}