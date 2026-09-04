package com.study.classloader.basic;

/**
 * 类初始化次数计数器。
 *
 * 为什么需要它：要验证"某个操作是否触发了类的初始化"，最简单可靠的办法是
 * 让被测类的 <clinit>（静态代码块）回调本计数器 +1，然后读计数。
 *
 * 关键设计：计数器必须在【另一个类】里。
 * 如果计数器放在被测类自己身上（如 static int INIT_COUNT），那么读取计数本身
 * 就是一次 getstatic 访问——按 JVM 规范会【触发】初始化，测试就测不准了。
 * 把计数放到独立类里，读取 InitRecorder.count() 与被测类完全无关。
 *
 * 配合 {@code ClassLoadingLifecycleDemo} 里运行时编译生成的 InitProbe 使用：
 * InitProbe 的静态代码块调用本类的 record()，测试逐个场景断言 count() 的变化。
 */
public final class InitRecorder {

    private static int count = 0;

    private InitRecorder() {
    }

    /** 供被测类的 <clinit> 调用：初始化执行一次就 +1。 */
    public static synchronized void record() {
        count++;
    }

    /** 读取当前累计的初始化次数（读取本类字段不会触发被测类初始化）。 */
    public static synchronized int count() {
        return count;
    }

    /** 每个测试场景开始前清零，保证场景间互不影响。 */
    public static synchronized void reset() {
        count = 0;
    }
}