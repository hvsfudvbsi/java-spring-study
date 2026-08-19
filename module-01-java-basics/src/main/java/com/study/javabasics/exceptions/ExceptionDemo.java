package com.study.javabasics.exceptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * 异常处理
 *
 * 异常体系：
 *   Throwable
 *    ├── Error            （JVM 级错误，如 OutOfMemoryError，不捕获）
 *    └── Exception
 *         ├── RuntimeException    （非受检异常：NPE、IndexOutOfBounds...）
 *         └── 其他受检异常        （编译期强制处理，如 IOException）
 *
 * 最佳实践：
 *   1. 不要捕获 Exception/Throwable 这种大而全的异常
 *   2. 不要吞异常（catch 后什么都不做）
 *   3. 使用 try-with-resources 自动关闭资源
 *   4. 精确抛出业务异常，配合 Spring 的全局异常处理（见 module-03）
 */
public class ExceptionDemo {

    public static void demo() {
        System.out.println("【1. 受检异常 vs 非受检异常】");
        try {
            Thread.sleep(100); // 受检异常 InterruptedException，编译器强制处理
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态（最佳实践）
            System.out.println("   捕获到 InterruptedException");
        }

        try {
            int result = 10 / 0; // 非受检异常 ArithmeticException，可以不处理
        } catch (ArithmeticException e) {
            System.out.println("   捕获到 ArithmeticException: " + e.getMessage());
        }

        System.out.println();
        System.out.println("【2. 多异常捕获 + finally】");
        try {
            String s = null;
            s.length();
        } catch (NullPointerException | IllegalArgumentException e) {
            // Java 7+ 可以用 | 一次捕获多个异常（必须是兄弟关系）
            System.out.println("   多异常捕获: " + e.getClass().getSimpleName());
        } finally {
            System.out.println("   finally 一定会执行（无论是否异常）");
        }

        System.out.println();
        System.out.println("【3. try-with-resources：自动关闭资源】");
        // 资源必须实现 AutoCloseable，用完后自动调用 close()，无需 finally
        try (BufferedReader reader = new BufferedReader(new StringReader("hello\nworld"))) {
            System.out.println("   读取第一行: " + reader.readLine());
        } catch (IOException e) {
            System.out.println("   IOException: " + e.getMessage());
        }
        System.out.println("   资源已自动关闭（无需手动 close）");

        System.out.println();
        System.out.println("【4. 自定义业务异常】");
        try {
            validateAge(15);
        } catch (IllegalArgumentException e) {
            System.out.println("   业务校验异常: " + e.getMessage());
        }
    }

    /** 业务校验：不合法就抛出带信息的异常，由上层统一处理 */
    private static void validateAge(int age) {
        if (age < 18) {
            throw new IllegalArgumentException("年龄必须大于等于 18，当前: " + age);
        }
    }
}
