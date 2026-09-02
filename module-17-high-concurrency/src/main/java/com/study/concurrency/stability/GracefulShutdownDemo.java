package com.study.concurrency.stability;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 优雅停机演示：shutdown() 与 shutdownNow() 的差别，以及为什么「发版/重启要先优雅停机」。
 *
 * <p>不优雅停机的问题：进程被强杀时，正在处理中的请求（扣款、写库、发消息）可能执行到一半，
 * 造成数据不一致或双写；优雅停机是「先停止接收新任务 → 让存量任务跑完 → 再释放资源退出」。
 *
 * <p>两个方法的区别（面试必考）：
 * <ul>
 *   <li>{@code shutdown()}：拒绝新任务，但<b>排空队列</b>，已提交任务全部执行完；不阻塞调用方。</li>
 *   <li>{@code shutdownNow()}：拒绝新任务，返回<b>未执行的排队任务</b>，并 interrupt 正在跑的任务。</li>
 *   <li>{@code awaitTermination}：阻塞等待结束，配合超时使用，避免无限等一个卡死的任务。</li>
 * </ul>
 *
 * <p>运行入口：{@link #demo()}；观察点：shutdown() 后 5 个任务全部完成、shutdownNow() 后排队任务
 * 被原样退回且正在跑的任务收到中断。
 */
public final class GracefulShutdownDemo {

    private GracefulShutdownDemo() {
    }

    /** 返回「已执行完成的任务数」。shutdown()：排队任务会继续执行，最终全部完成。 */
    public static int shutdownDrainsQueued(int taskCount, long taskMillis) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        // 单线程池 + 慢任务：保证后面的任务一定还排在队列里（否则无法观察「排空」语义）
        for (int i = 0; i < taskCount; i++) {
            pool.submit(() -> sleepQuietly(taskMillis));
        }
        pool.shutdown(); // 拒绝新任务，但队列里的照常执行
        boolean done = pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        return done ? taskCount : -1;
    }

    /** shutdownNow()：返回被中断的「正在跑」任务数、被退回的「排队」任务数。 */
    public static ShutdownResult shutdownNowInterrupts(int taskCount, long taskMillis)
            throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        pool.submit(() -> {
            running.countDown();
            try {
                Thread.sleep(taskMillis * 10); // 故意睡得比 taskMillis 久，确保被中断时还在跑
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.countDown(); // 证明 shutdownNow 的中断真的送达了
            }
        });
        running.await(); // 等第一个任务真正开始跑，再触发停机
        for (int i = 1; i < taskCount; i++) {
            pool.submit(() -> sleepQuietly(taskMillis));
        }
        List<Runnable> pending = pool.shutdownNow(); // 返回排队未执行的任务
        boolean interruptedReceived = interrupted.await(2, TimeUnit.SECONDS);
        return new ShutdownResult(pending.size(), interruptedReceived ? 1 : 0);
    }

    /** 停机结果：pendingCount=被退回的排队任务数，interruptedRunCount=被中断的运行中任务数。 */
    public record ShutdownResult(int pendingCount, int interruptedRunCount) {
    }

    /** 演示两种停机的完整差异。 */
    public static void demo() throws InterruptedException {
        System.out.println("【优雅停机演示】单线程池，先提交 5 个慢任务（每个 80ms）");

        System.out.println("  ① shutdown()：拒绝新任务、排空队列");
        int done = shutdownDrainsQueued(5, 80);
        System.out.println("    完成的任务数=" + done + "（=5 说明队列被排空，存量任务全部执行完）");

        System.out.println("  ② shutdownNow()：中断正在跑的任务、退回排队任务");
        ShutdownResult r = shutdownNowInterrupts(5, 80);
        System.out.println("    被退回的排队任务=" + r.pendingCount()
                + "，被中断的运行中任务=" + r.interruptedRunCount()
                + "（正在跑的被 interrupt，剩下 4 个没跑的原样退回）");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}