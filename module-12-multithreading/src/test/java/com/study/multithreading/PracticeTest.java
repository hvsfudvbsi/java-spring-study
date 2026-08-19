package com.study.multithreading;

import com.study.multithreading.practice.BankTransferDemo.Account;
import com.study.multithreading.practice.BankTransferDemo.Bank;
import com.study.multithreading.practice.HighConcurrencyGatewayDemo;
import com.study.multithreading.practice.TicketSaleDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实操示例验证：
 *   1. 抢票：CAS 写法卖出票数 == 总票数（不超卖）
 *   2. 银行转账：并发转账后总资产不变、余额不为负、无死锁
 *   3. 生产者-消费者：订单全部处理完（见 ProducerConsumerDemo 的统计逻辑）
 */
class PracticeTest {

    @Test
    @DisplayName("抢票：CAS 写法不超卖，卖出票数恰好等于总票数")
    void ticketSaleNoOversell() throws Exception {
        // 通过反射调用私有方法验证（与 main 中同一套逻辑）
        var runAtomic = TicketSaleDemo.class.getDeclaredMethod("runAtomic", int.class, int.class);
        runAtomic.setAccessible(true);
        int sold = (int) runAtomic.invoke(null, TicketSaleDemo.TOTAL_TICKETS, TicketSaleDemo.WINDOWS);
        assertEquals(TicketSaleDemo.TOTAL_TICKETS, sold, "CAS 写法不应超卖");
    }

    @Test
    @DisplayName("抢票：去掉 sleep 的错误写法也不会少卖（超卖与否是概率事件）")
    void wrongWriteNoSleepNeverSellsFewer() throws Exception {
        // 确定性性质：每次扣减都发生在"看到正数"之后，所以只会多卖、不会少卖
        // 是否超卖取决于调度（无 sleep 时竞争窗口仅几纳秒），属概率事件，无法稳定断言
        // —— 这正是 README 练习 2 要解释的点：不超卖 ≠ 代码正确
        var runWrongNoSleep = TicketSaleDemo.class.getDeclaredMethod("runWrongNoSleep", int.class, int.class);
        runWrongNoSleep.setAccessible(true);
        int total = 10_000;
        int sold = (int) runWrongNoSleep.invoke(null, total, 8);
        assertTrue(sold >= total, "错误写法只会多卖、不会少卖，实际卖出 " + sold);
    }

    @Test
    @DisplayName("抢票：Semaphore(3) 限制并发写库不超过 3，且 CAS 不超卖")
    void semaphoreLimitsDbConcurrency() throws Exception {
        var runSaleWithDb = TicketSaleDemo.class.getDeclaredMethod(
                "runSaleWithDb", int.class, int.class, Semaphore.class, int.class);
        runSaleWithDb.setAccessible(true);

        // 实验组：Semaphore(3) —— 并发写库数被硬性限制在 3 以内（信号量保证，确定性）
        var limited = (TicketSaleDemo.DbSaleResult) runSaleWithDb.invoke(null, 100, 20, new Semaphore(3), 5);
        assertEquals(100, limited.sold(), "CAS 不应超卖");
        assertTrue(limited.maxConcurrentDb() <= 3,
                "Semaphore(3) 下并发写库不能超过 3，实际 " + limited.maxConcurrentDb());

        // 对照组：不限流 —— 20 个窗口自由写库，并发应能明显超过 3（证明信号量确实在起作用）
        var unlimited = (TicketSaleDemo.DbSaleResult) runSaleWithDb.invoke(null, 100, 20, null, 5);
        assertEquals(100, unlimited.sold(), "CAS 不应超卖");
        assertTrue(unlimited.maxConcurrentDb() > 3,
                "不限流时并发写库应能超过 3，实际 " + unlimited.maxConcurrentDb());
    }

    @Test
    @DisplayName("银行转账：并发转账后总资产不变，且余额不为负")
    void bankTransferInvariant() throws Exception {
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            accounts.add(new Account("ACC-" + i, 10_000));
        }
        Bank bank = new Bank(accounts);
        long initial = bank.totalBalance();

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Random random = new Random(42);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        String from = "ACC-" + (1 + random.nextInt(5));
                        String to = "ACC-" + (1 + random.nextInt(5));
                        long amount = 1 + random.nextInt(200);
                        bank.transfer(from, to, amount);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(initial, bank.totalBalance(), "总资产必须保持不变");
        accounts.forEach(a -> assertTrue(a.balance() >= 0, a.id() + " 余额不能为负"));
    }

    @Test
    @DisplayName("银行转账：余额不足时整笔失败，不产生负数")
    void bankTransferInsufficientFunds() {
        List<Account> accounts = List.of(
                new Account("A", 100),
                new Account("B", 0));
        Bank bank = new Bank(accounts);
        assertFalse(bank.transfer("B", "A", 50), "余额不足应失败");
        assertEquals(100, bank.account("A").balance());
        assertEquals(0, bank.account("B").balance());
        assertTrue(bank.transfer("A", "B", 50), "余额足够应成功");
        assertEquals(50, bank.account("A").balance());
        assertEquals(50, bank.account("B").balance());
    }

    @Test
    @DisplayName("银行转账：自己转自己直接成功，金额不变")
    void bankTransferToSelf() {
        Bank bank = new Bank(List.of(new Account("A", 100)));
        assertTrue(bank.transfer("A", "A", 100));
        assertEquals(100, bank.account("A").balance());
    }

    @Test
    @DisplayName("转账日志：只记录成功的真实转账（自转不计）")
    void transferLogRecordsSuccessfulTransfers() {
        Bank bank = new Bank(List.of(
                new Account("A", 100),
                new Account("B", 50)));
        assertFalse(bank.transfer("B", "A", 60), "余额不足应失败");
        assertTrue(bank.transfer("A", "B", 30), "余额足够应成功");
        assertTrue(bank.transfer("A", "A", 100), "自己转自己应成功");

        var log = bank.transferLog();
        assertEquals(1, log.size(), "只记录成功的真实转账");
        assertEquals("A", log.get(0).fromId());
        assertEquals("B", log.get(0).toId());
        assertEquals(30, log.get(0).amount());
    }

    @Test
    @DisplayName("转账日志：并发下日志条数与成功笔数一致（无丢失、无重复）")
    void transferLogThreadSafe() throws Exception {
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            accounts.add(new Account("ACC-" + i, 10_000));
        }
        Bank bank = new Bank(accounts);
        int threads = 8;
        int perThread = 500;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger();
        Random random = new Random(42);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String from = "ACC-" + (1 + random.nextInt(5));
                        String to;
                        do {
                            to = "ACC-" + (1 + random.nextInt(5));
                        } while (to.equals(from));
                        if (bank.transfer(from, to, 1 + random.nextInt(200))) {
                            successes.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(successes.get(), bank.transferLog().size(),
                "日志条数必须等于成功笔数（ConcurrentLinkedQueue 并发追加无丢失）");
    }

    @Test
    @DisplayName("高并发请求：虚拟线程并行执行阻塞任务，耗时远小于串行")
    void virtualThreadsRunBlockingTasksConcurrently() throws Exception {
        // 10 个请求、每个阻塞 100ms：虚拟线程全部并行 -> 总耗时应远小于串行的 1000ms
        long elapsed = HighConcurrencyGatewayDemo.processWithVirtualThreads(10, 100);
        assertTrue(elapsed < 600, "虚拟线程应并行执行阻塞任务，实际耗时 " + elapsed + "ms");
    }

    @Test
    @DisplayName("高并发请求：单线程固定池串行执行（对比基线，验证耗时逻辑成立）")
    void fixedPoolSerialBaseline() throws Exception {
        // 10 个请求、单线程固定池：串行 -> 总耗时至少 10 x 100ms
        long elapsed = HighConcurrencyGatewayDemo.processWithFixedPool(10, 1, 100);
        assertTrue(elapsed >= 700, "单线程固定池应串行执行，实际耗时 " + elapsed + "ms");
    }
}
