package com.study.javabasics.optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional 测试示例
 */
class OptionalDemoTest {

    @Test
    @DisplayName("orElse 提供默认值")
    void orElse() {
        Optional<String> empty = Optional.empty();
        assertEquals("默认值", empty.orElse("默认值"));
    }

    @Test
    @DisplayName("orElseGet 惰性计算默认值")
    void orElseGet() {
        Optional<String> empty = Optional.empty();
        assertEquals("计算出的默认值", empty.orElseGet(() -> "计算出的默认值"));
    }

    @Test
    @DisplayName("orElseThrow 抛出指定异常")
    void orElseThrow() {
        Optional<String> empty = Optional.empty();
        assertThrows(IllegalStateException.class,
                () -> empty.orElseThrow(() -> new IllegalStateException("空值!")));
    }

    @Test
    @DisplayName("map 链式转换")
    void map() {
        Optional<String> result = Optional.of("hello")
                .map(String::toUpperCase)
                .map(s -> s + "!");
        assertEquals(Optional.of("HELLO!"), result);
    }

    @Test
    @DisplayName("filter 过滤为 empty")
    void filter() {
        Optional<Integer> result = Optional.of(5).filter(n -> n > 10);
        assertTrue(result.isEmpty());
    }
}
