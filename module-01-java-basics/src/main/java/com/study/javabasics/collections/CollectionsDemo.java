package com.study.javabasics.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 集合框架（Java Collections Framework）
 *
 * 三大接口体系：
 *   List  -> ArrayList / LinkedList         有序、可重复
 *   Set   -> HashSet / LinkedHashSet / TreeSet  无序、不可重复
 *   Map   -> HashMap / LinkedHashMap / TreeMap  键值对
 *
 * 面试高频考点：
 *   1. HashMap 底层是数组 + 链表 + 红黑树，初始容量 16，负载因子 0.75
 *   2. HashSet 底层就是 HashMap（value 用固定的 Object 占位）
 *   3. TreeMap/TreeSet 基于红黑树，key 必须可比较（Comparable 或传入 Comparator）
 *   4. 线程安全场景使用 ConcurrentHashMap（不要用 Hashtable / synchronizedMap）
 */
public class CollectionsDemo {

    public static void demo() {
        System.out.println("【1. List】ArrayList 自动扩容，按索引随机访问快");
        List<String> list = new ArrayList<>();
        list.add("Java");
        list.add("Spring");
        list.add("Spring Boot");
        list.add("Java"); // List 允许重复
        System.out.println("   list = " + list);
        System.out.println("   第 1 个元素 = " + list.get(1));
        System.out.println("   Java 出现次数 = " + java.util.Collections.frequency(list, "Java"));

        System.out.println();
        System.out.println("【2. Set】自动去重，HashSet 无序");
        Set<String> set = new HashSet<>();
        set.add("Java");
        set.add("Spring");
        set.add("Java"); // 重复元素被忽略
        System.out.println("   set = " + set + "（大小=" + set.size() + "）");

        System.out.println();
        System.out.println("【3. TreeSet】去重 + 自动排序");
        Set<Integer> sortedSet = new TreeSet<>(List.of(5, 1, 4, 2, 3, 1));
        System.out.println("   sortedSet = " + sortedSet);

        System.out.println();
        System.out.println("【4. HashMap】键值对，无序");
        Map<String, Integer> map = new HashMap<>();
        map.put("Java", 1);
        map.put("Spring", 2);
        map.put("Spring Boot", 3);
        map.putIfAbsent("Java", 100); // 只有 key 不存在时才放入
        System.out.println("   map = " + map);
        System.out.println("   getOrDefault = " + map.getOrDefault("Python", 0));

        System.out.println();
        System.out.println("【5. LinkedHashMap】保持插入顺序（常用于 LRU 缓存）");
        Map<String, Integer> linkedMap = new LinkedHashMap<>();
        linkedMap.put("a", 1);
        linkedMap.put("b", 2);
        linkedMap.put("c", 3);
        System.out.println("   linkedMap = " + linkedMap.keySet());

        System.out.println();
        System.out.println("【6. TreeMap】按键排序");
        Map<String, Integer> treeMap = new TreeMap<>();
        treeMap.put("banana", 2);
        treeMap.put("apple", 1);
        treeMap.put("cherry", 3);
        System.out.println("   treeMap = " + treeMap.keySet());

        System.out.println();
        System.out.println("【7. 不可变集合】List.of / Set.of / Map.of");
        List<String> immutable = List.of("a", "b", "c");
        System.out.println("   immutable = " + immutable + "（不可增删改）");
    }
}
