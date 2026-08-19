package com.study.javabasics.optional;

import java.util.Optional;

/**
 * Optional：用于优雅地表达"可能为空"的值，避免 NullPointerException
 *
 * 核心思想：能用 Optional 就别返回 null，调用方必须显式处理"值不存在"的情况。
 *
 * 常用方法：
 *   of / ofNullable / empty       创建
 *   isPresent / isEmpty           判断
 *   orElse / orElseGet / orElseThrow  兜底取值
 *   map / flatMap / filter        链式转换
 *   ifPresent                     存在时消费
 */
public class OptionalDemo {

    /** 模拟根据 id 查用户，可能查不到 */
    private static Optional<String> findUser(int id) {
        return id > 0 ? Optional.of("用户" + id) : Optional.empty();
    }

    public static void demo() {
        System.out.println("【1. 创建 Optional】");
        Optional<String> present = Optional.of("hello");
        Optional<String> empty = Optional.empty();
        Optional<String> nullable = Optional.ofNullable(null); // 允许 null
        System.out.println("   present.isPresent() = " + present.isPresent()
                + ", empty.isEmpty() = " + empty.isEmpty()
                + ", nullable = " + nullable);

        System.out.println();
        System.out.println("【2. orElse / orElseGet / orElseThrow 兜底取值】");
        // orElse：无论如何都会计算默认值；orElseGet：只有为空时才计算（推荐，性能更好）
        String v1 = findUser(1).orElse("默认用户");
        String v2 = findUser(-1).orElseGet(() -> "动态生成的默认用户");
        System.out.println("   v1 = " + v1 + ", v2 = " + v2);

        try {
            findUser(-1).orElseThrow(() -> new IllegalStateException("用户不存在！"));
        } catch (IllegalStateException e) {
            System.out.println("   orElseThrow 抛出了异常: " + e.getMessage());
        }

        System.out.println();
        System.out.println("【3. map 链式转换（值存在才转换，避免 NPE）】");
        // 传统写法：if (user != null) { String name = user.getName(); ... }
        // Optional 写法：
        Optional<String> upper = findUser(1)
                .map(String::toUpperCase)          // 转换
                .map(s -> "前缀-" + s);            // 再转换
        System.out.println("   map 链: " + upper.orElse("(空)"));

        System.out.println();
        System.out.println("【4. filter 条件过滤】");
        Optional<String> filtered = findUser(1)
                .filter(s -> s.length() > 10);     // 不满足条件 -> 变 empty
        System.out.println("   filter 结果: " + filtered.orElse("(被过滤为空)"));

        System.out.println();
        System.out.println("【5. ifPresent 存在即消费】");
        findUser(1).ifPresent(s -> System.out.println("   找到了: " + s));

        System.out.println();
        System.out.println("【6. 反模式警告】不要这样做：");
        System.out.println("   ❌ Optional.ofNullable(x).get() 直接 get（可能抛 NoSuchElementException）");
        System.out.println("   ❌ 用 Optional 做参数或字段类型（应只用于返回值）");
    }
}
