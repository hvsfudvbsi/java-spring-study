package com.study.classloader.basic;

import com.study.classloader.delegation.custom.FileClassLoader;
import com.study.classloader.util.RuntimeCompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 类加载过程（加载 → 验证 → 准备 → 解析 → 初始化）与"初始化触发时机"演示。
 *
 * JVM 把 .class 字节码变成可用的类，要经历五个阶段（《Java 虚拟机规范》第 5 章）：
 *
 *  1. 加载（Loading）      ：类加载器把 .class 字节码读进内存，生成 java.lang.Class 对象；
 *  2. 验证（Verification） ：字节码安全校验（拒绝非法字节码）；
 *  3. 准备（Preparation）  ：为静态字段分配内存并赋默认值（0 / null / false）；
 *  4. 解析（Resolution）   ：把常量池中的符号引用替换为直接引用（类/字段/方法）；
 *  5. 初始化（Initialization）：执行 <clinit>（静态字段赋值语句 + 静态代码块）。
 *
 * 前四个阶段通常"被动"发生（用到才做），只有初始化可以被明确观察到——
 * 因为它会执行用户写的静态代码。本 Demo 用 {@link InitRecorder} 探测"初始化是否发生"。
 *
 * 关键结论（面试高频）：
 *  - 会触发初始化：new 对象、反射 Class.forName(name)（默认 initialize=true）、
 *    访问非常量静态字段（getstatic）、调用静态方法、初始化子类前先初始化父类；
 *  - 不会触发初始化：访问编译期常量（static final 字面量，字节码是 ldc 且值已内联）、
 *    Class.forName(name, false, loader)、ClassLoader.loadClass()、通过数组引用类。
 *
 * 测试要点：测试必须在同一个 JVM 里反复验证"未初始化"状态，而普通类的 <clinit>
 * 一个 JVM 里只执行一次——所以这里用 {@link RuntimeCompiler} 在运行时生成探针类
 * InitProbe（每次用全新的类加载器加载 = 全新的类 = 全新的初始化机会），
 * 保证每个测试场景都从"未初始化"开始，结果确定。
 *
 * 测试入口：{@code ClassLoadingLifecycleDemoTest}
 */
public class ClassLoadingLifecycleDemo {

    /** 探针类全限定名（运行时编译生成，应用类路径上没有它）。 */
    public static final String PROBE_NAME = "com.study.classloader.basic.generated.InitProbe";

    /** 探针访问器：用字节码访问探针的静态字段（ldc 常量 / getstatic 可变字段）。 */
    public static final String ACCESSOR_NAME = "com.study.classloader.basic.generated.ProbeAccessor";

    /**
     * 探针类源码：
     *  - CONSTANT 是编译期常量，被访问器引用时会被 javac 内联成 ldc；
     *  - MUTABLE 的赋值和静态代码块都在 <clinit> 里，执行时会调用 InitRecorder.record()。
     */
    public static final String PROBE_SOURCE = """
            package com.study.classloader.basic.generated;

            import com.study.classloader.basic.InitRecorder;

            /** 运行时生成的初始化探针：<clinit> 执行时向 InitRecorder 记账。 */
            public class InitProbe {
                public static final String CONSTANT = "hello";
                public static String MUTABLE = touch();

                static {
                    InitRecorder.record();
                }

                static String touch() {
                    return "mutable-value";
                }
            }
            """;

    /**
     * 探针访问器源码：
     *  - readConstant()：javac 把 InitProbe.CONSTANT 内联成 ldc "hello"，
     *    运行时不触碰 InitProbe，不触发初始化；
     *  - readMutable()：getstatic InitProbe.MUTABLE，触发初始化。
     */
    public static final String ACCESSOR_SOURCE = """
            package com.study.classloader.basic.generated;

            /** 运行时生成的访问器：用真实字节码访问探针静态字段。 */
            public class ProbeAccessor {
                public static String readConstant() {
                    return InitProbe.CONSTANT;
                }

                public static String readMutable() {
                    return InitProbe.MUTABLE;
                }
            }
            """;

    /**
     * 编译探针 + 访问器到 workDir 并返回目录。
     * 测试通过 @TempDir 调用。
     */
    public Path prepareProbe(Path workDir) throws IOException {
        return RuntimeCompiler.compileAll(workDir.resolve("probe"), Map.of(
                PROBE_NAME, PROBE_SOURCE,
                ACCESSOR_NAME, ACCESSOR_SOURCE));
    }

    /** 每次调用都创建一个全新的类加载器 → 加载到的是"全新未初始化"的探针类。 */
    public ClassLoader freshLoader(Path probeDir) {
        return new FileClassLoader(probeDir, getClass().getClassLoader());
    }

    /** 场景 A：ClassLoader.loadClass 只加载、不初始化。返回操作后的初始化次数。 */
    public int loadClassOnly(ClassLoader loader) throws ClassNotFoundException {
        loader.loadClass(PROBE_NAME);
        return InitRecorder.count();
    }

    /** 场景 B：Class.forName(name, false, loader) 只加载 + 链接、不初始化。 */
    public int forNameWithoutInit(ClassLoader loader) throws ClassNotFoundException {
        Class.forName(PROBE_NAME, false, loader);
        return InitRecorder.count();
    }

    /** 场景 C：Class.forName(name, true, loader) 触发初始化。 */
    public int forNameWithInit(ClassLoader loader) throws ClassNotFoundException {
        Class.forName(PROBE_NAME, true, loader);
        return InitRecorder.count();
    }

    /** 场景 D：通过访问器读编译期常量（ldc 内联），不触发初始化，返回常量的值。 */
    public String readConstant(ClassLoader loader) throws Exception {
        Class<?> accessor = loader.loadClass(ACCESSOR_NAME);
        return (String) accessor.getMethod("readConstant").invoke(null);
    }

    /** 场景 E：通过访问器读非常量静态字段（getstatic），触发初始化。 */
    public String readMutable(ClassLoader loader) throws Exception {
        Class<?> accessor = loader.loadClass(ACCESSOR_NAME);
        return (String) accessor.getMethod("readMutable").invoke(null);
    }

    /** 场景 F：new 对象触发初始化（构造 <init> 前必须先完成类 <clinit>）。 */
    public int instantiate(ClassLoader loader) throws Exception {
        Class<?> clazz = loader.loadClass(PROBE_NAME);
        clazz.getDeclaredConstructor().newInstance();
        return InitRecorder.count();
    }

    /** 交互式演示：按场景顺序执行并打印每次的初始化次数。 */
    public void demo() throws Exception {
        System.out.println("========== 类加载生命周期与初始化触发时机 ==========");
        Path workDir = Files.createTempDirectory("lifecycle-demo");
        Path probeDir = prepareProbe(workDir);
        System.out.println("（探针类 InitProbe 已运行时编译，每次用全新加载器 = 全新未初始化类）");
        System.out.println();

        InitRecorder.reset();
        ClassLoader loaderA = freshLoader(probeDir);
        System.out.println("【A】ClassLoader.loadClass             → 初始化次数 = " + loadClassOnly(loaderA) + "（不初始化）");

        InitRecorder.reset();
        ClassLoader loaderB = freshLoader(probeDir);
        System.out.println("【B】Class.forName(name,false,loader)  → 初始化次数 = " + forNameWithoutInit(loaderB) + "（不初始化）");

        InitRecorder.reset();
        ClassLoader loaderC = freshLoader(probeDir);
        System.out.println("【C】读编译期常量（ldc 内联）           → 初始化次数 = " + InitRecorder.count()
                + "（不初始化），常量值 = " + readConstant(loaderC));

        InitRecorder.reset();
        ClassLoader loaderD = freshLoader(probeDir);
        System.out.println("【D】Class.forName(name,true,loader)   → 初始化次数 = " + forNameWithInit(loaderD) + "（触发初始化）");

        InitRecorder.reset();
        ClassLoader loaderE = freshLoader(probeDir);
        String mutableValue = readMutable(loaderE);
        System.out.println("【E】读非常量静态字段（getstatic）       → 初始化次数 = " + InitRecorder.count()
                + "（触发初始化），字段值 = " + mutableValue);

        InitRecorder.reset();
        ClassLoader loaderF = freshLoader(probeDir);
        System.out.println("【F】new 对象                          → 初始化次数 = " + instantiate(loaderF) + "（触发初始化）");

        System.out.println();
        System.out.println("结论：<clinit> 每个类在 JVM 里只执行一次；");
        System.out.println("      触发它的是 new / 反射 / getstatic / 静态方法调用，");
        System.out.println("      不触发它的是 loadClass / forName(false) / 编译期常量。");
    }

    /**
     * 演示"初始化子类必须先初始化父类"（JVM 规范保证）：
     * Child 的 <clinit> 依赖 Parent，所以触发 Child 初始化时会先完成 Parent 初始化。
     */
    public void demoParentBeforeChild() {
        System.out.println("【G】初始化子类前先初始化父类：");
        Child.init();
    }

    /** 父类：静态代码块打印标记（测试通过捕获标准输出断言顺序）。 */
    public static class Parent {
        static {
            System.out.println("    Parent 静态代码块执行");
        }

        static void touch() {
            // 占位：确保 Parent 有可被初始化的内容。
        }
    }

    /** 子类：初始化时先触发父类初始化。 */
    public static class Child extends Parent {
        static {
            System.out.println("    Child 静态代码块执行");
        }

        static void init() {
            // 访问本类静态方法，触发 Child 初始化（连带 Parent 初始化）。
        }
    }
}