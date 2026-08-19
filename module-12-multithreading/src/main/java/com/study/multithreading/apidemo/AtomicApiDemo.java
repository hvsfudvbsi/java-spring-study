package com.study.multithreading.apidemo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 原子类（Atomic*）方法用例（常用 + 不常用）
 *
 * 原理：CAS（Compare-And-Swap，比较并交换），无锁、乐观并发。
 *   compareAndSet(期望值, 新值)：内存值 == 期望值才更新，返回是否成功。
 *   配合自旋循环即可实现线程安全的"读-改-写"（如 incrementAndGet 内部就是 CAS 循环）。
 *
 * 对比：
 *   synchronized 悲观锁（阻塞）    -> 适合竞争激烈、临界区代码长
 *   Atomic* CAS 乐观锁（自旋）     -> 适合竞争不激烈、临界区代码短
 */
public class AtomicApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== AtomicInteger 常用方法 ==========");

        AtomicInteger ai = new AtomicInteger(10);
        System.out.println("  get()=" + ai.get());
        System.out.println("  set(20) 后=" + ai.getAndSet(20));  // getAndSet：返回旧值并设新值
        System.out.println("  incrementAndGet()=" + ai.incrementAndGet());  // ++i
        System.out.println("  getAndIncrement()=" + ai.getAndIncrement());  // i++（返回旧值）
        System.out.println("  addAndGet(5)=" + ai.addAndGet(5));
        System.out.println("  getAndAdd(-3)=" + ai.getAndAdd(-3) + "（返回旧值，再减 3）");
        System.out.println("  compareAndSet(期望22, 100)=" + ai.compareAndSet(22, 100)
                + "（CAS 成功，当前值=" + ai.get() + "）");
        System.out.println("  compareAndSet(期望22, 999)=" + ai.compareAndSet(22, 999)
                + "（CAS 失败：期望值不匹配）");

        System.out.println();
        System.out.println("========== Atomic 不常用但有用的方法 ==========");

        // ---- updateAndGet / getAndUpdate：基于当前值的函数式更新 ----
        AtomicInteger u = new AtomicInteger(2);
        System.out.println("  updateAndGet(x -> x*x)=" + u.updateAndGet(x -> x * x));   // 4
        System.out.println("  getAndUpdate(x -> x+1)=" + u.getAndUpdate(x -> x + 1) + "（返回旧值 4，现为 5）");

        // ---- accumulateAndGet / getAndAccumulate：带初始值的二元累积 ----
        AtomicInteger a = new AtomicInteger(3);
        System.out.println("  accumulateAndGet(4, Integer::max)=" + a.accumulateAndGet(4, Integer::max)); // max(3,4)=4
        System.out.println("  getAndAccumulate(10, Integer::sum)=" + a.getAndAccumulate(10, Integer::sum)
                + "（返回旧值 4，现为 14）");

        // ---- lazySet：低开销延迟写（只保证最终可见，不保证立即可见） ----
        AtomicInteger lazy = new AtomicInteger(1);
        lazy.lazySet(2);
        System.out.println("  lazySet(2) 后 get()=" + lazy.get() + "（写屏障更轻，适合低频更新）");

        // ---- weakCompareAndSetPlain：弱 CAS（不保证顺序性，性能更优的极客用法） ----
        AtomicInteger w = new AtomicInteger(5);
        System.out.println("  weakCompareAndSetPlain(5, 6)=" + w.weakCompareAndSetPlain(5, 6));

        System.out.println();
        System.out.println("========== AtomicReference：原子引用（不常用但强大） ==========");

        // ---- AtomicReference：对对象做 CAS（如无锁栈/链表、缓存更新） ----
        AtomicReference<String> ref = new AtomicReference<>("old");
        System.out.println("  compareAndSet(\"old\", \"new\")=" + ref.compareAndSet("old", "new")
                + "，get()=" + ref.get());
        // CAS 自旋更新模式：循环直到成功（无锁编程基础）
        ref.updateAndGet(s -> s + "!");

        System.out.println();
        System.out.println("========== ABA 问题与 AtomicStampedReference ==========");

        // ---- ABA 问题：A->B->A，CAS 只看值发现没变，但中间被改过 ----
        // AtomicStampedReference 用"版本号"解决：比较 值+版本号
        AtomicStampedReference<String> stamped = new AtomicStampedReference<>("A", 0);
        int[] holder = new int[1];
        String value = stamped.get(holder);
        System.out.println("  AtomicStampedReference 当前=" + value + "，版本=" + holder[0]);
        boolean ok = stamped.compareAndSet("A", "B", holder[0], holder[0] + 1);
        System.out.println("  compareAndSet(值+版本号一起比较)=" + ok + "，版本号=" + stamped.getStamp());

        System.out.println();
        System.out.println("========== LongAdder：高并发计数器（比 AtomicLong 更快） ==========");

        // ---- LongAdder：分段累加，竞争激烈时性能远胜 AtomicLong（统计场景首选） ----
        LongAdder adder = new LongAdder();
        Thread[] threads = new Thread[20];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100_000; j++) {
                    adder.increment();
                }
            });
            threads[i].start();
        }
        for (Thread th : threads) {
            th.join();
        }
        System.out.println("  LongAdder 20 线程 x 10 万次累加 sum()=" + adder.sum() + "（期望 2,000,000）");
        System.out.println("  sumThenReset()=" + adder.sumThenReset() + "（累加后清零，现为 " + adder.sum() + "）");
    }
}
