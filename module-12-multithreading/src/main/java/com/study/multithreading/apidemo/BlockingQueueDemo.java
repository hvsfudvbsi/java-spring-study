package com.study.multithreading.apidemo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * 阻塞队列（BlockingQueue）方法用例（常用 + 不常用）
 *
 * 四组方法（面试必问，按失败行为区分）：
 *   | 操作 | 抛异常     | 返回特殊值   | 阻塞        | 超时退出               |
 *   |------|-----------|-------------|-------------|------------------------|
 *   | 入队  | add(e)    | offer(e)    | put(e)      | offer(e, 超时, 单位)    |
 *   | 出队  | remove()  | poll()      | take()      | poll(超时, 单位)        |
 *   | 查看  | element() | peek()      | 不支持      | 不支持                 |
 *
 * 生产-消费场景：生产用 put（队列满则阻塞等），消费用 take（队列空则阻塞等）。
 */
public class BlockingQueueDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 四组入队/出队方法 ==========");

        BlockingQueue<Integer> q = new ArrayBlockingQueue<>(3);   // 有界队列，容量 3

        // 第一组：抛异常
        q.add(1);
        q.add(2);
        q.add(3);
        System.out.println("  add 满队列时抛 IllegalStateException（队列已满: " + q + "）");
        try {
            q.add(4);
        } catch (IllegalStateException e) {
            System.out.println("    -> add(4) 抛 " + e.getClass().getSimpleName());
        }
        System.out.println("  element() 只看队头=" + q.element() + "（空队列时抛 NoSuchElementException）");
        q.clear();

        // 第二组：返回特殊值（不抛异常，推荐日常用）
        System.out.println("  offer(1)=" + q.offer(1) + "，poll()=" + q.poll() + "，peek()=" + q.peek() + "（空返回 null）");
        System.out.println("  remove() 空队列时抛 NoSuchElementException");
        try {
            q.remove();
        } catch (java.util.NoSuchElementException e) {
            System.out.println("    -> remove() 抛 " + e.getClass().getSimpleName());
        }

        // 第三组：阻塞（put/take），生产消费最常用
        BlockingQueue<Integer> blocking = new LinkedBlockingQueue<>(1);
        Thread producer = new Thread(() -> {
            try {
                blocking.put(1);
                System.out.println("  put(1) 完成");
                blocking.put(2);          // 队列满，阻塞直到被 take
                System.out.println("  put(2) 完成（消费者取走后继续）");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");
        Thread consumer = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("  take() 取出=" + blocking.take());
                Thread.sleep(200);
                System.out.println("  take() 取出=" + blocking.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        // 第四组：超时退出（offer/poll 带时间）
        BlockingQueue<Integer> timed = new ArrayBlockingQueue<>(1);
        boolean offered = timed.offer(1, 100, TimeUnit.MILLISECONDS);
        System.out.println("  offer(1, 100ms) 空队列立即成功=" + offered);
        boolean full = timed.offer(2, 100, TimeUnit.MILLISECONDS);
        System.out.println("  offer(2, 100ms) 满队列超时返回=" + full + "（不会无限阻塞）");
        Integer got = timed.poll(100, TimeUnit.MILLISECONDS);
        System.out.println("  poll(100ms) 取出=" + got);

        System.out.println();
        System.out.println("========== BlockingQueue 不常用但有用的方法 ==========");

        // ---- remainingCapacity / drainTo / contains ----
        ArrayBlockingQueue<Integer> cap = new ArrayBlockingQueue<>(10);
        cap.add(1);
        cap.add(2);
        cap.add(3);
        System.out.println("  remainingCapacity()=" + cap.remainingCapacity() + "（还剩 7 个位置）");
        List<Integer> drained = new ArrayList<>();
        cap.drainTo(drained, 2);           // 一次性取走最多 2 个
        System.out.println("  drainTo(目标, 2) 批量取走=" + drained + "，队列剩余=" + cap);

        System.out.println();
        System.out.println("========== 各实现类特点 ==========");

        // ---- ArrayBlockingQueue：有界数组，必须指定容量；公平性可选 ----
        System.out.println("  ArrayBlockingQueue : 有界、数组实现，容量必填（默认非公平）");
        // ---- LinkedBlockingQueue：链表，默认无界（可指定容量） ----
        System.out.println("  LinkedBlockingQueue: 链表实现，默认 Integer.MAX_VALUE 无界（注意 OOM）");
        // ---- PriorityBlockingQueue：按优先级出队 ----
        PriorityBlockingQueue<Integer> pq = new PriorityBlockingQueue<>();
        pq.add(30);
        pq.add(10);
        pq.add(20);
        System.out.println("  PriorityBlockingQueue 出队顺序=" + pq.poll() + "->" + pq.poll() + "->" + pq.poll()
                + "（最小优先）");
        // ---- SynchronousQueue：不存数据，put 必须等 take（直接交接） ----
        SynchronousQueue<String> handoff = new SynchronousQueue<>();
        System.out.println("  SynchronousQueue 不缓冲数据：offer 没人接就失败");
        System.out.println("    offer(\"x\")=" + handoff.offer("x") + "（无消费者，立即失败）");
        // ---- LinkedTransferQueue：transfer 直接交接给等待的消费者 ----
        LinkedTransferQueue<String> tq = new LinkedTransferQueue<>();
        Thread taker = new Thread(() -> {
            try {
                System.out.println("  transfer 交接成功，消费者收到=" + tq.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        taker.start();
        tq.transfer("订单A");   // 阻塞直到有消费者接手（真正的"交接"语义）
        taker.join();
        // ---- DelayQueue：延迟到期的任务才能被 take ----
        DelayQueue<DelayedTask> delayQueue = new DelayQueue<>();
        delayQueue.put(new DelayedTask("任务1", 100));
        delayQueue.put(new DelayedTask("任务2", 300));
        System.out.println("  DelayQueue take 会阻塞到延迟到期：" + delayQueue.take() + "（100ms 后到期）");
        System.out.println("  DelayQueue take：" + delayQueue.take() + "（300ms 后到期）");
    }

    /** DelayQueue 元素：必须实现 Delayed（getDelay + compareTo） */
    static class DelayedTask implements Delayed {
        private final String name;
        private final long deadline;   // 到期时间戳（纳秒）

        DelayedTask(String name, long delayMillis) {
            this.name = name;
            this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(deadline, ((DelayedTask) o).deadline);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
