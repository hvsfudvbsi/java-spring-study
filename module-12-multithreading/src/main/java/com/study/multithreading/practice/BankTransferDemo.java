package com.study.multithreading.practice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 实操示例三：银行转账系统（完整并发实操，面试高频）
 *
 * 场景：多个账户之间并发转账，要求：
 *   1. 线程安全：余额不能出现负数、不能丢钱
 *   2. 不死锁：两笔反向转账同时进行时不能互相等待
 *   3. 不变量：无论怎么转，所有账户余额总和不变
 *
 * 技术点：
 *   - ReentrantLock 保护余额（比 synchronized 灵活）
 *   - 按账户 id 排序后加锁（全局顺序加锁）—— 死锁的经典解法
 *   - 余额不足返回失败，不做部分扣款
 *   - ConcurrentLinkedQueue 转账日志：无锁并发追加所有成功转账，最后统计条数与累计金额
 *
 * 运行：mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.BankTransferDemo
 */
public class BankTransferDemo {

    /** 一条成功转账记录 */
    public record TransferRecord(String fromId, String toId, long amount) {
        @Override
        public String toString() {
            return fromId + " -> " + toId + " ¥" + amount;
        }
    }

    /** 账户：余额用 ReentrantLock 保护 */
    public static final class Account {
        private final String id;
        private final ReentrantLock lock = new ReentrantLock();
        private long balance;

        public Account(String id, long initialBalance) {
            this.id = id;
            this.balance = initialBalance;
        }

        public String id() {
            return id;
        }

        public long balance() {
            lock.lock();
            try {
                return balance;
            } finally {
                lock.unlock();
            }
        }

        void credit(long amount) {
            balance += amount;
        }

        void debit(long amount) {
            balance -= amount;
        }
    }

    /** 银行：管理账户 + 转账服务 */
    public static final class Bank {
        private final Map<String, Account> accounts = new LinkedHashMap<>();
        // 转账日志：ConcurrentLinkedQueue 无锁并发追加，天然线程安全（不用锁、不用 synchronized）
        private final Queue<TransferRecord> transferLog = new ConcurrentLinkedQueue<>();

        public Bank(List<Account> accountList) {
            accountList.forEach(a -> accounts.put(a.id(), a));
        }

        public Account account(String id) {
            return accounts.get(id);
        }

        public long totalBalance() {
            return accounts.values().stream().mapToLong(Account::balance).sum();
        }

        /** 转账日志的不可变快照（所有线程结束后统计用） */
        public List<TransferRecord> transferLog() {
            return List.copyOf(transferLog);
        }

        /** 线程安全地追加一条成功转账记录 */
        private void logTransfer(String fromId, String toId, long amount) {
            transferLog.add(new TransferRecord(fromId, toId, amount));
        }

        /**
         * 安全转账：按账户 id 排序后加锁，避免死锁。
         * 流程：小 id 先加锁 -> 校验余额 -> 扣减/增加 -> 依次解锁。
         *
         * @return true=转账成功；false=余额不足
         */
        public boolean transfer(String fromId, String toId, long amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("转账金额不能为负");
            }
            if (fromId.equals(toId)) {
                return true;   // 自己转自己
            }
            Account from = accounts.get(fromId);
            Account to = accounts.get(toId);
            if (from == null || to == null) {
                throw new IllegalArgumentException("账户不存在");
            }

            // 关键：按 id 字典序确定加锁顺序（全局一致顺序 = 不可能死锁）
            Account first = from.id().compareTo(to.id()) < 0 ? from : to;
            Account second = first == from ? to : from;

            first.lock.lock();
            try {
                second.lock.lock();
                try {
                    if (from.balance < amount) {
                        return false;          // 余额不足，整笔失败（不会部分扣款）
                    }
                    from.debit(amount);
                    to.credit(amount);
                    logTransfer(fromId, toId, amount);   // 只记录成功的真实转账（自转不记）
                    return true;
                } finally {
                    second.lock.unlock();
                }
            } finally {
                first.lock.unlock();
            }
        }

        /**
         * 危险写法（仅供学习，绝不要在生产使用）：
         * 按参数顺序加锁，两个线程分别执行 transfer(a,b) 与 transfer(b,a) 时
         * 会互相持有对方等待的锁 -> 死锁。
         */
        @SuppressWarnings("unused")
        public boolean transferDeadlockDemo(String fromId, String toId, long amount) {
            Account from = accounts.get(fromId);
            Account to = accounts.get(toId);
            from.lock.lock();       // 线程1: 锁A；线程2: 锁B —— 互相等对方手里的锁
            try {
                to.lock.lock();
                try {
                    if (from.balance < amount) {
                        return false;
                    }
                    from.debit(amount);
                    to.credit(amount);
                    return true;
                } finally {
                    to.lock.unlock();
                }
            } finally {
                from.lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 银行转账系统（并发安全 + 不死锁） ==========");

        // 5 个账户，初始各 10000
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            accounts.add(new Account("ACC-" + i, 10_000));
        }
        Bank bank = new Bank(accounts);
        System.out.println("  初始总资产 = " + bank.totalBalance());

        // 8 个线程各执行 500 次随机转账
        int threads = 8;
        int transfersPerThread = 500;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Random random = new Random(42);   // 固定种子，结果可复现

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < transfersPerThread; i++) {
                        String from = "ACC-" + (1 + random.nextInt(5));
                        String to;
                        do {
                            to = "ACC-" + (1 + random.nextInt(5));
                        } while (to.equals(from));   // 避免自转，保证"成功笔数 = 日志条数"
                        long amount = 1 + random.nextInt(200);
                        bank.transfer(from, to, amount);   // 结果可能失败（余额不足），忽略即可
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "transfer-" + t).start();
        }

        start.countDown();
        done.await();

        long total = bank.totalBalance();
        System.out.println("  " + threads + " 个线程 x " + transfersPerThread + " 次转账完成");
        System.out.println("  转账后总资产 = " + total + (total == 50_000
                ? " ✅ 不变量成立（一分钱没丢）"
                : " ❌ 金额不一致！"));
        accounts.forEach(a -> System.out.println("    " + a.id() + " 余额 = " + a.balance()
                + (a.balance() < 0 ? "（负数！）" : "")));

        // ---- 转账日志统计：ConcurrentLinkedQueue 线程安全收集的所有成功转账 ----
        List<TransferRecord> log = bank.transferLog();
        int attempts = threads * transfersPerThread;
        long moved = log.stream().mapToLong(TransferRecord::amount).sum();
        System.out.println("  转账日志：成功 " + log.size() + " 笔（共尝试 " + attempts
                + "，余额不足失败 " + (attempts - log.size()) + " 笔），累计金额 ¥" + moved);
        System.out.println("  日志前 3 条: " + log.stream().limit(3).toList());

        System.out.println("  死锁防护：按账户 id 排序加锁 -> 转账期间无死锁、无阻塞");
    }
}
