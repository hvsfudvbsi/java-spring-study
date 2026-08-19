package com.study.javabasics.lambda;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lambda 表达式与函数式接口
 *
 * Lambda 的本质：函数式接口（只有一个抽象方法的接口）的简洁实现。
 *
 * JDK 内置四大函数式接口（务必熟记）：
 *   Predicate<T>    T -> boolean        test(T)
 *   Function<T,R>   T -> R              apply(T)
 *   Consumer<T>     T -> void           accept(T)
 *   Supplier<T>     () -> T             get()
 */
public class LambdaDemo {

    /** 自定义函数式接口：可以用 @FunctionalInterface 注解强制约束 */
    @FunctionalInterface
    interface Calculator {
        int calc(int a, int b);
    }

    public static void demo() {
        System.out.println("【1. 自定义函数式接口】");
        Calculator add = (a, b) -> a + b;
        Calculator multiply = (a, b) -> a * b;
        Calculator max = Math::max; // 方法引用：类名::静态方法
        System.out.println("   add(3,4)=" + add.calc(3, 4)
                + ", multiply(3,4)=" + multiply.calc(3, 4)
                + ", max(3,4)=" + max.calc(3, 4));

        System.out.println();
        System.out.println("【2. Predicate：判断】");
        Predicate<String> isEmpty = String::isEmpty;
        Predicate<String> lengthGt5 = s -> s.length() > 5;
        Predicate<String> combined = isEmpty.negate().and(lengthGt5); // 组合谓词
        System.out.println("   \"hello\" 非空且长度>5? " + combined.test("hello"));
        System.out.println("   \"hello world\" 非空且长度>5? " + combined.test("hello world"));

        System.out.println();
        System.out.println("【3. Function：转换】");
        Function<String, Integer> parseLen = String::length;
        Function<Integer, String> toHex = Integer::toHexString;
        System.out.println("   \"Java\" 长度转十六进制 = " + parseLen.andThen(toHex).apply("Java"));

        System.out.println();
        System.out.println("【4. Consumer：消费】");
        Consumer<String> print = System.out::println;
        List.of("a", "b", "c").forEach(print); // forEach 接收的就是 Consumer

        System.out.println();
        System.out.println("【5. Supplier：生产】");
        Supplier<Double> random = Math::random;
        System.out.println("   随机数 = " + random.get());

        System.out.println();
        System.out.println("【6. 方法引用的四种形式】");
        // 1) 类名::静态方法   2) 实例::实例方法   3) 类名::实例方法   4) 构造器引用 类名::new
        List<String> names = List.of("java", "spring", "boot");
        System.out.println("   names.stream().map(String::toUpperCase) = "
                + names.stream().map(String::toUpperCase).toList()); // 类名::实例方法
    }
}
