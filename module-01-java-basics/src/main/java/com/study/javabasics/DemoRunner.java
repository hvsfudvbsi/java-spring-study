package com.study.javabasics;

import com.study.javabasics.collections.CollectionsDemo;
import com.study.javabasics.concurrency.ConcurrencyDemo;
import com.study.javabasics.exceptions.ExceptionDemo;
import com.study.javabasics.generics.GenericsDemo;
import com.study.javabasics.lambda.LambdaDemo;
import com.study.javabasics.optional.OptionalDemo;
import com.study.javabasics.records.RecordDemo;
import com.study.javabasics.stream.StreamDemo;

/**
 * 运行方式：直接执行本类的 main 方法
 * （IDEA 中右键 Run，或命令行：java com.study.javabasics.DemoRunner）
 *
 * 本模块是纯 Java 模块，不依赖 Spring，
 * 用于复习和巩固 Java 21 的核心语言特性。
 */
public class DemoRunner {

    public static void main(String[] args) {
        System.out.println("========== Java 基础专题 Demo ==========");
        System.out.println();

        CollectionsDemo.demo();
        System.out.println();

        StreamDemo.demo();
        System.out.println();

        OptionalDemo.demo();
        System.out.println();

        LambdaDemo.demo();
        System.out.println();

        GenericsDemo.demo();
        System.out.println();

        RecordDemo.demo();
        System.out.println();

        ExceptionDemo.demo();
        System.out.println();

        ConcurrencyDemo.demo();
        System.out.println();

        System.out.println("========== 全部 Demo 执行完毕 ==========");
    }
}
