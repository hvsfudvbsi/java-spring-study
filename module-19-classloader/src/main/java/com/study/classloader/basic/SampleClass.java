package com.study.classloader.basic;

/**
 * 类初始化触发时机演示用的"目标类"（main 源码版本，供交互式运行观察）。
 *
 * 观察方法：{@link ClassLoadingLifecycleDemo} 会打印 InitRecorder.count() 的变化，
 * 以及每个操作对应的 JVM 字节码指令（new / getstatic / ldc 等）。
 *
 * 关键设计：
 * 1. {@code static final String CONSTANT} 是"编译期常量"（javac 直接把值写进引用处，
 *    不生成 getstatic 字节码），所以访问它【不会】触发初始化；
 * 2. {@code static String MUTABLE} 的赋值发生在类初始化阶段（<clinit> 方法里），
 *    所以访问它【会】触发初始化；
 * 3. 静态代码块调用 {@link InitRecorder#record()}，把初始化次数记录在独立类里，
 *    避免"读计数本身也是访问被测类静态字段"导致误触发初始化。
 *
 * 注意：测试用的是运行时编译生成的 InitProbe（同样结构），因为测试需要在同一个
 * JVM 里反复验证"未初始化"状态，而本类初始化一次后状态就固定了（<clinit> 只跑一次）。
 */
public class SampleClass {

    /** 编译期常量：javac 编译时把 "hello" 直接内联到调用处，访问它不会触发初始化。 */
    public static final String CONSTANT = "hello";

    /** 非常量静态字段：赋值语句在 <clinit> 中执行，访问它会触发初始化。 */
    public static String MUTABLE = createValue();

    static {
        // 这一段静态代码块属于 <clinit> 方法，只有"初始化"阶段才会执行。
        InitRecorder.record();
        System.out.println("    [SampleClass 静态代码块执行] 初始化次数 -> " + InitRecorder.count());
    }

    private static String createValue() {
        return "mutable-value";
    }
}