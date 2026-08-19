package com.study.multithreading.apidemo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

/**
 * 并发集合方法用例（常用 + 不常用）
 *
 * 选型（面试必问）：
 *   | 场景                | 推荐                         |
 *   |---------------------|------------------------------|
 *   | 并发读写 Map        | ConcurrentHashMap（分段/桶锁）|
 *   | 有序并发 Map/Set    | ConcurrentSkipListMap/Set    |
 *   | 读多写极少 List     | CopyOnWriteArrayList        |
 *   | 生产者-消费者队列   | BlockingQueue（见下个 Demo） |
 *
 * 禁忌：Hashtable / Collections.synchronizedMap 全表锁，并发性能差，已被替代。
 */
public class ConcurrentCollectionDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== ConcurrentHashMap 常用方法 ==========");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("java", 1);
        map.put("spring", 2);
        System.out.println("  put/get/size=" + map.size());

        // ---- computeIfAbsent：key 不存在才计算（并发安全的"缓存"写法） ----
        map.computeIfAbsent("java", k -> expensiveLoad(k));      // 已存在，不覆盖
        map.computeIfAbsent("netty", k -> expensiveLoad(k));     // 不存在，计算并放入
        System.out.println("  computeIfAbsent：java=" + map.get("java") + "（保持 1），netty=" + map.get("netty"));

        // ---- putIfAbsent：仅当 key 不存在时写入（替代"先查再写"的非原子写法） ----
        map.putIfAbsent("java", 999);
        System.out.println("  putIfAbsent 对已存在的 java 无效，仍为 " + map.get("java"));

        // ---- forEach：并发安全遍历 ----
        map.forEach((k, v) -> System.out.println("  forEach: " + k + "=" + v));

        System.out.println();
        System.out.println("========== ConcurrentHashMap 不常用但有用的方法 ==========");

        // ---- merge：合并（统计场景神器，原子"累加"） ----
        ConcurrentHashMap<String, LongAdder> wordCount = new ConcurrentHashMap<>();
        for (String word : List.of("a", "b", "a", "c", "a", "b")) {
            wordCount.computeIfAbsent(word, k -> new LongAdder()).increment();
        }
        System.out.println("  computeIfAbsent+LongAdder 统计词频: " + wordCount);

        ConcurrentHashMap<String, Integer> counter = new ConcurrentHashMap<>();
        List<String> words = List.of("a", "b", "a", "c", "a", "b");
        words.forEach(w -> counter.merge(w, 1, Integer::sum));   // 原子累加
        System.out.println("  merge 统计词频: " + counter);

        // ---- compute：基于旧值计算新值（可删除：返回 null 即删除） ----
        counter.compute("a", (k, v) -> v == null ? 1 : v + 1);
        System.out.println("  compute 对 a 再 +1 = " + counter.get("a"));
        counter.compute("z", (k, v) -> null);   // 返回 null -> 不放入
        System.out.println("  compute 返回 null 则不入 map，size=" + counter.size());

        // ---- search / reduce：并发聚合（大 Map 多核加速） ----
        Integer maxVal = counter.reduceEntries(2, Map.Entry::getValue, Integer::max);
        System.out.println("  reduceEntries 求最大值=" + maxVal);
        String found = counter.search(2, (k, v) -> v == 3 ? k : null);
        System.out.println("  search 找 value==3 的 key=" + found);

        // ---- mappingCount：long 类型的 size（元素超 2^31 时 size() 不准） ----
        System.out.println("  mappingCount()=" + counter.mappingCount());

        // ---- keySet / values 视图：与 map 联动 ----
        var keys = counter.keySet();
        System.out.println("  keySet 视图（可加可删，会同步到 map）=" + keys);

        System.out.println();
        System.out.println("========== CopyOnWriteArrayList / Set ==========");

        // ---- CopyOnWriteArrayList：写时复制（读无锁，写复制整数组），读多写极少用 ----
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        list.add("a");
        list.add("b");
        System.out.println("  CopyOnWriteArrayList: " + list);
        // 迭代时修改不会抛 ConcurrentModificationException（迭代的是快照）
        for (String s : list) {
            list.add("c");
        }
        System.out.println("  迭代中 add 不抛异常（快照迭代），size=" + list.size());

        CopyOnWriteArraySet<String> set = new CopyOnWriteArraySet<>();
        set.add("x");
        System.out.println("  CopyOnWriteArraySet 底层是 COW list，add=" + set.add("x") + "（重复返回 false）");

        System.out.println();
        System.out.println("========== ConcurrentSkipListMap：有序并发 Map ==========");

        // ---- 跳表实现：线程安全 + 有序（范围查询场景） ----
        ConcurrentSkipListMap<Integer, String> skip = new ConcurrentSkipListMap<>();
        skip.put(3, "c");
        skip.put(1, "a");
        skip.put(2, "b");
        System.out.println("  跳表自动排序 firstKey=" + skip.firstKey()
                + "，lastKey=" + skip.lastKey()
                + "，ceilingKey(2)=" + skip.ceilingKey(2)
                + "，subMap 视图=" + skip.subMap(1, true, 3, true));

        System.out.println();
        System.out.println("========== 实战：多线程统计（验证并发安全） ==========");

        ConcurrentHashMap<String, Integer> shared = new ConcurrentHashMap<>();
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 1000; j++) {
                        shared.merge("count", 1, Integer::sum);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        System.out.println("  10 线程 x 1000 次 merge 累加 = " + shared.get("count") + "（期望 10000，无锁丢失）");
    }

    private static int expensiveLoad(String key) {
        return key.length() * 10;
    }
}
