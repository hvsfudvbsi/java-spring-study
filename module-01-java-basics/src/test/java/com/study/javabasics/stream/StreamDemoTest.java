package com.study.javabasics.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stream API 测试示例
 * 运行：mvn test -pl module-01-java-basics
 */
class StreamDemoTest {

    @Test
    @DisplayName("filter+map 筛选与转换")
    void filterAndMap() {
        List<String> names = List.of("Alice", "Bob", "Charlie", "David");
        List<String> result = names.stream()
                .filter(n -> n.length() > 4)
                .map(String::toUpperCase)
                .toList();

        assertEquals(List.of("ALICE", "CHARLIE", "DAVID"), result);
    }

    @Test
    @DisplayName("distinct+sort 去重排序")
    void distinctAndSort() {
        List<Integer> result = List.of(3, 1, 2, 1, 3)
                .stream()
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    @DisplayName("groupingBy 分组")
    void groupingBy() {
        record Item(String category, int price) {}

        List<Item> items = List.of(
                new Item("food", 10),
                new Item("food", 20),
                new Item("drink", 5)
        );

        var byCategory = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(Item::category));

        assertEquals(2, byCategory.get("food").size());
        assertEquals(1, byCategory.get("drink").size());
    }

    @Test
    @DisplayName("reduce 归约求和")
    void reduce() {
        int sum = List.of(1, 2, 3, 4, 5).stream().reduce(0, Integer::sum);
        assertEquals(15, sum);
    }

    @Test
    @DisplayName("match 匹配操作")
    void match() {
        List<Integer> nums = List.of(1, 2, 3);
        assertTrue(nums.stream().anyMatch(n -> n == 2));
        assertTrue(nums.stream().allMatch(n -> n > 0));
    }
}
