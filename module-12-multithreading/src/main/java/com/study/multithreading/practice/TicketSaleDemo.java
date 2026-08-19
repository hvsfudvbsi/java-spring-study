package com.study.multithreading.practice;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实操示例二：抢票系统（并发超卖问题实战）
 *
 * 场景：100 张票，10 个售票窗口（线程）同时卖。
 *
 * 三种写法对比：
 *   1. 错误写法：先查再减（check-then-act 非原子）-> 超卖，票卖成负数
 *   2. synchronized 写法：整段加锁 -> 正确但略笨重
 *   3. AtomicInteger CAS 写法：无锁自旋 -> 正确且轻量
 *
 * 额外实验（README 练习 2）：错误写法去掉 Thread.sleep(1)、加大票量后，
 *   超卖大概率"看不见"了——但 bug 没有消失，只是竞争窗口从毫秒级缩到纳秒级，
 *   复现概率变低而已。修复必须靠原子操作（synchronized / CAS），不是去掉 sleep。
 *
 * 练习 5（README）：用 Semaphore 限制同时最多 3 个窗口"写数据库"。
 *   - 票数：CAS 保证不超卖（并发安全）
 *   - 写库：Semaphore(3) 模拟数据库连接池，同时最多 3 个窗口在写（限流）
 *   - 核心：Semaphore 管"并发上限"，CAS/锁管"数据安全"——是两个不同的问题
 *
 * 核心：多线程下"读-改-写"必须原子，否则丢失更新。
 *
 * 运行：mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.TicketSaleDemo
 */
public class TicketSaleDemo {

    public static final int TOTAL_TICKETS = 100;
    public static final int WINDOWS = 10;

    public static void main(String[] args) throws Exception {
        System.out.println("========== 抢票系统：3 种写法对比 ==========");

        // ---- 写法一：错误写法（带 sleep 放大竞争窗口，超卖稳定复现） ----
        int wrong = runWrong(TOTAL_TICKETS, WINDOWS);
        System.out.println("  错误写法（int 先查再减 + sleep 放大竞争）卖出的票 = " + wrong
                + (wrong > TOTAL_TICKETS ? " ❌ 超卖！" : "（恰好没触发，但这是运气）"));

        // ---- 练习 2：错误写法去掉 sleep + 加大票量，观察是否还超卖 ----
        int bigTotal = 100_000;
        int wrongNoSleep = runWrongNoSleep(bigTotal, 20);
        System.out.println("  错误写法（去掉 sleep，10 万张票 20 窗口）卖出的票 = " + wrongNoSleep
                + (wrongNoSleep > bigTotal ? " ❌ 超卖！" : "（这次没超卖——但代码依然是错的！）"));

        // ---- 写法二：synchronized ----
        int sync = runSynchronized(TOTAL_TICKETS, WINDOWS);
        System.out.println("  synchronized 写法卖出的票 = " + sync
                + (sync == TOTAL_TICKETS ? " ✅ 正确" : " ❌"));

        // ---- 写法三：AtomicInteger CAS ----
        int cas = runAtomic(TOTAL_TICKETS, WINDOWS);
        System.out.println("  AtomicInteger CAS 写法卖出的票 = " + cas
                + (cas == TOTAL_TICKETS ? " ✅ 正确" : " ❌"));

        System.out.println();
        System.out.println("  ── 为什么去掉 sleep 后不超卖，代码却还是错的？ ──");
        System.out.println("  1. sleep 只是把\"检查-扣减\"的竞争窗口人为放大（毫秒级），让超卖更容易复现");
        System.out.println("  2. 去掉 sleep，竞争窗口缩到几纳秒，超卖概率变低——但窗口依然存在");
        System.out.println("  3. \"这次没超卖\" 是运气（概率问题），不是正确性；票数翻倍/机器调度一变就可能超卖");
        System.out.println("  4. 正确修复：让\"检查+扣减\"成为原子操作（synchronized 或 CAS），而不是去掉 sleep");

        System.out.println();
        System.out.println("========== 练习 5：Semaphore 限制写库并发 ==========");
        System.out.println("  场景：卖出的每张票都要写数据库，数据库连接池最多 3 个连接");

        // 对照组：不限流 —— 20 个窗口自由写库，看并发能冲到多高
        DbSaleResult noLimit = runSaleWithDb(TOTAL_TICKETS, 20, null, 5);
        System.out.println("  无信号量：20 窗口卖 " + TOTAL_TICKETS + " 张票，最大并发写库 = "
                + noLimit.maxConcurrentDb() + " 次（数据库连接被打爆 ❌）");

        // 实验组：Semaphore(3) —— 同时最多 3 个窗口写库
        DbSaleResult limited = runSaleWithDb(TOTAL_TICKETS, 20, new Semaphore(3), 5);
        System.out.println("  Semaphore(3)：20 窗口卖 " + TOTAL_TICKETS + " 张票，最大并发写库 = "
                + limited.maxConcurrentDb() + " 次（限流生效 ✅），卖出 "
                + limited.sold() + "/" + TOTAL_TICKETS + "（CAS 不超卖）");
        System.out.println("  对比：Semaphore 管\"同时最多几个人干活\"（限流/连接池）；"
                + "CAS/锁管\"数据安全\"——两个不同的问题");
    }

    /** 错误写法：非原子的\"检查-扣减\"，并发下会超卖（sleep 放大竞争窗口，便于稳定复现） */
    private static int runWrong(int totalTickets, int windows) throws InterruptedException {
        int[] tickets = {totalTickets};   // 普通 int，无同步
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(windows);

        for (int w = 0; w < windows; w++) {
            new Thread(() -> {
                try {
                    start.await();
                    while (tickets[0] > 0) {          // 检查
                        Thread.sleep(1);              // 放大竞争窗口，更容易触发超卖
                        tickets[0]--;                 // 扣减（两步之间可能被其他线程插队）
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "window-wrong-" + w).start();
        }
        start.countDown();
        done.await();
        return totalTickets - tickets[0];   // 实际卖出的票数（可能超过总量）
    }

    /**
     * 错误写法去掉 sleep：竞争窗口从毫秒级缩到纳秒级，超卖概率骤降。
     * 注意：这只是"更难复现"，check-then-act 依旧非原子，bug 没有消失。
     */
    private static int runWrongNoSleep(int totalTickets, int windows) throws InterruptedException {
        int[] tickets = {totalTickets};
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(windows);

        for (int w = 0; w < windows; w++) {
            new Thread(() -> {
                try {
                    start.await();
                    while (tickets[0] > 0) {   // 检查
                        tickets[0]--;          // 扣减（无 sleep：两步之间只有几纳秒，但仍可能被插队）
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "window-nosleep-" + w).start();
        }
        start.countDown();
        done.await();
        return totalTickets - tickets[0];
    }

    /** 正确写法一：synchronized 整段加锁，串行化扣减 */
    private static int runSynchronized(int totalTickets, int windows) throws InterruptedException {
        int[] tickets = {totalTickets};
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(windows);

        for (int w = 0; w < windows; w++) {
            new Thread(() -> {
                try {
                    start.await();
                    while (true) {
                        synchronized (tickets) {       // 加锁保证"检查+扣减"原子
                            if (tickets[0] <= 0) {
                                break;
                            }
                            tickets[0]--;
                        }
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "window-sync-" + w).start();
        }
        start.countDown();
        done.await();
        return totalTickets - tickets[0];
    }

    /**
     * 卖票 + 写库：票数用 CAS 保证不超卖；db 为 null 时不限流，否则限制并发写库数。
     *
     * @param db      数据库连接池信号量（null = 不限流）
     * @param dbMillis 每次"写库"的阻塞耗时（模拟 I/O）
     */
    private static DbSaleResult runSaleWithDb(int totalTickets, int windows, Semaphore db, int dbMillis)
            throws InterruptedException {
        AtomicInteger tickets = new AtomicInteger(totalTickets);
        AtomicInteger dbConcurrent = new AtomicInteger();   // 当前正在写库的窗口数
        AtomicInteger maxDbConcurrent = new AtomicInteger(); // 观察到的最大并发写库数
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(windows);

        for (int w = 0; w < windows; w++) {
            new Thread(() -> {
                try {
                    start.await();
                    while (true) {
                        int current = tickets.get();
                        if (current <= 0) {
                            break;
                        }
                        // CAS 卖票（不超卖）
                        if (tickets.compareAndSet(current, current - 1)) {
                            simulateDbWrite(db, dbMillis, dbConcurrent, maxDbConcurrent);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "window-db-" + w).start();
        }
        start.countDown();
        done.await();
        return new DbSaleResult(totalTickets - tickets.get(), maxDbConcurrent.get());
    }

    /** 模拟一次数据库写入：写库前拿许可（限流），记录并发数，再阻塞 dbMillis */
    private static void simulateDbWrite(Semaphore db, int dbMillis,
                                        AtomicInteger concurrent, AtomicInteger maxConcurrent)
            throws InterruptedException {
        if (db != null) {
            db.acquire();   // 拿许可：没许可就排队等（同时最多 permits 个窗口在写库）
        }
        try {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            Thread.sleep(dbMillis);   // 模拟数据库写入耗时（阻塞 I/O）
            concurrent.decrementAndGet();
        } finally {
            if (db != null) {
                db.release();   // 还许可
            }
        }
    }

    /** 限流卖票结果：卖出票数 + 观察到的最大并发写库数 */
    public record DbSaleResult(int sold, int maxConcurrentDb) {
    }

    /** 正确写法二：AtomicInteger CAS 自旋，无锁 */
    private static int runAtomic(int totalTickets, int windows) throws InterruptedException {
        AtomicInteger tickets = new AtomicInteger(totalTickets);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(windows);

        for (int w = 0; w < windows; w++) {
            new Thread(() -> {
                try {
                    start.await();
                    while (true) {
                        int current = tickets.get();
                        if (current <= 0) {
                            break;
                        }
                        // CAS：只有当前值没被改过才扣减，失败则重试（自旋）
                        if (tickets.compareAndSet(current, current - 1)) {
                            Thread.sleep(1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "window-cas-" + w).start();
        }
        start.countDown();
        done.await();
        return totalTickets - tickets.get();
    }
}
