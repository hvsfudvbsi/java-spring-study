package com.study.multithreading.apidemo;

import java.util.Map;

/**
 * Thread 类方法用例（常用 + 不常用）
 *
 * 线程生命周期：NEW -> RUNNABLE -> BLOCKED / WAITING / TIMED_WAITING -> TERMINATED
 *
 * 面试必问：
 *   1. sleep 与 wait 的区别：sleep 不释放锁，wait 释放锁
 *   2. interrupt 不是强制终止，而是协作式中断（由业务代码检查中断标志后自行退出）
 *   3. 守护线程（Daemon）：所有非守护线程结束后 JVM 直接退出，守护线程被"抛弃"
 */
public class ThreadApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== Thread 常用方法 ==========");

        // ---- currentThread / getName / getId / getState ----
        Thread main = Thread.currentThread();
        System.out.println("  当前线程=" + main.getName()
                + "，id=" + main.getId()
                + "，state=" + main.getState());

        // ---- start / setName / join ----
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(150);   // 模拟耗时
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断标志（规范写法）
            }
            System.out.println("  子线程 " + Thread.currentThread().getName() + " 执行完毕");
        }, "worker-1");
        System.out.println("  创建后 state=" + t.getState() + "（NEW，还没 start）");
        t.start();                    // 启动线程（只能调一次，重复调抛 IllegalThreadStateException）
        System.out.println("  start 后 state=" + t.getState());
        t.join();                     // 主线程阻塞，等待子线程结束（可传超时 join(1000)）
        System.out.println("  join 等待结束后 state=" + t.getState() + "（TERMINATED）");

        // ---- sleep：让出 CPU 但不释放锁 ----
        System.out.println("  sleep(50) 前时间=" + System.currentTimeMillis() % 100000);
        Thread.sleep(50);
        System.out.println("  sleep(50) 后时间=" + System.currentTimeMillis() % 100000);

        // ---- interrupt：协作式中断 ----
        Thread busy = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // 模拟忙等（自旋），检查中断标志自行退出
                Thread.onSpinWait();
            }
            System.out.println("  isInterrupted() 检测到中断，退出自旋");
        }, "busy-1");
        busy.start();
        Thread.sleep(50);
        busy.interrupt();             // 设置中断标志（不是强制杀死！）
        busy.join();

        // ---- setDaemon：守护线程（JVM 不等待它退出） ----
        Thread daemon = new Thread(() -> {
            try {
                Thread.sleep(10_000); // 若是非守护线程，JVM 会等它 10 秒才退出
            } catch (InterruptedException ignored) {
            }
            System.out.println("  守护线程结束");
        }, "daemon-1");
        daemon.setDaemon(true);       // 必须在 start 之前设置
        daemon.start();
        System.out.println("  isDaemon=" + daemon.isDaemon() + "（守护线程：主线程结束 JVM 直接退出）");

        System.out.println();
        System.out.println("========== Thread 不常用但有用的方法 ==========");

        // ---- holdsLock：判断当前线程是否持有某对象锁（调试死锁利器） ----
        Object lock = new Object();
        System.out.println("  未加锁时 Thread.holdsLock(lock)=" + Thread.holdsLock(lock));
        synchronized (lock) {
            System.out.println("  已加锁时 Thread.holdsLock(lock)=" + Thread.holdsLock(lock));
        }

        // ---- setPriority：线程优先级（1-10，仅作调度参考，不保证生效） ----
        Thread high = new Thread(() -> {}, "high");
        high.setPriority(Thread.MAX_PRIORITY);
        System.out.println("  设置 MAX_PRIORITY=" + high.getPriority());

        // ---- yield：主动让出 CPU（提示调度器，不保证让出） ----
        Thread.yield();

        // ---- getStackTrace / getAllStackTraces：抓取线程堆栈（排查死锁/卡死） ----
        StackTraceElement[] stack = main.getStackTrace();
        System.out.println("  getStackTrace 当前栈帧数=" + stack.length);
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        System.out.println("  getAllStackTraces 当前存活线程数=" + all.size());

        // ---- setUncaughtExceptionHandler：线程内异常兜底（比 try-catch 更优雅） ----
        Thread risky = new Thread(() -> {
            throw new IllegalStateException("业务异常");
        }, "risky-1");
        risky.setUncaughtExceptionHandler((th, ex) ->
                System.out.println("  uncaughtExceptionHandler 捕获 " + th.getName() + " 的异常: " + ex.getMessage()));
        risky.start();
        risky.join();

        // ---- dumpStack：打印当前线程堆栈（调试用） ----
        // Thread.dumpStack();

        // ---- 已废弃方法（了解即可，永远不要用！） ----
        // stop()   : 强制终止线程，会破坏锁与数据一致性
        // suspend()/resume() : 挂起/恢复，容易造成死锁
        System.out.println("  stop/suspend/resume 已废弃（JDK 标记 @Deprecated(forRemoval=true)），禁止使用");
    }
}
