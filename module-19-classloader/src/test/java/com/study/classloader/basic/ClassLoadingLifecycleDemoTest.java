package com.study.classloader.basic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类加载生命周期：验证哪些操作触发初始化、哪些不触发。
 *
 * 每个场景都用【全新的类加载器】加载运行时编译的探针类（InitProbe），
 * 确保每个场景都从"未初始化"状态开始，结果不依赖测试执行顺序。
 */
class ClassLoadingLifecycleDemoTest {

    private final ClassLoadingLifecycleDemo demo = new ClassLoadingLifecycleDemo();

    @TempDir
    Path tempDir;

    private Path probeDir;

    @BeforeEach
    void setUp() throws Exception {
        probeDir = demo.prepareProbe(tempDir);
    }

    @Test
    @DisplayName("loadClass 只加载不初始化：初始化次数保持 0")
    void loadClassDoesNotInitialize() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals(0, demo.loadClassOnly(loader), "loadClass 不应触发 <clinit>");
    }

    @Test
    @DisplayName("Class.forName(name, false, loader) 只加载+链接不初始化")
    void forNameWithoutInitDoesNotInitialize() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals(0, demo.forNameWithoutInit(loader), "forName(false) 不应触发 <clinit>");
    }

    @Test
    @DisplayName("读取编译期常量（ldc 内联）不初始化，且能拿到常量值")
    void readCompileTimeConstantDoesNotInitialize() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals("hello", demo.readConstant(loader));
        assertEquals(0, InitRecorder.count(), "javac 把常量内联成 ldc，访问不触发 <clinit>");
    }

    @Test
    @DisplayName("Class.forName(name, true, loader) 触发初始化")
    void forNameWithInitTriggersInitialization() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals(1, demo.forNameWithInit(loader), "forName(true) 应触发 <clinit> 一次");
    }

    @Test
    @DisplayName("读取非常量静态字段（getstatic）触发初始化")
    void readMutableFieldTriggersInitialization() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals("mutable-value", demo.readMutable(loader));
        assertEquals(1, InitRecorder.count(), "getstatic 非常量字段应触发 <clinit> 一次");
    }

    @Test
    @DisplayName("new 对象触发初始化")
    void newInstanceTriggersInitialization() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals(1, demo.instantiate(loader), "new 之前必须先完成 <clinit>");
    }

    @Test
    @DisplayName("<clinit> 每个类只执行一次：重复触发不会再次初始化")
    void clinitRunsOnlyOncePerClass() throws Exception {
        InitRecorder.reset();
        ClassLoader loader = demo.freshLoader(probeDir);
        assertEquals(1, demo.forNameWithInit(loader));
        // 已初始化后，再次访问静态字段 / 再 new，<clinit> 不会重跑。
        assertEquals("mutable-value", demo.readMutable(loader));
        assertEquals(1, demo.instantiate(loader));
        assertEquals(1, InitRecorder.count(), "<clinit> 同一类在 JVM 里只执行一次");
    }

    @Test
    @DisplayName("初始化子类前必须先初始化父类：Parent 静态块先于 Child 输出")
    void parentInitializedBeforeChild() {
        // 捕获标准输出，断言输出顺序符合 JVM 规范保证。
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            demo.demoParentBeforeChild();
        } finally {
            System.setOut(original);
        }
        String out = buffer.toString(StandardCharsets.UTF_8);
        int parentPos = out.indexOf("Parent 静态代码块执行");
        int childPos = out.indexOf("Child 静态代码块执行");
        assertTrue(parentPos >= 0 && childPos >= 0, "父类和子类的静态块都应执行: " + out);
        assertTrue(parentPos < childPos, "父类 <clinit> 必须先于子类完成");
    }

    @Test
    @DisplayName("全新加载器加载同名探针 = 全新未初始化类（每个类加载器一个命名空间）")
    void freshLoaderMeansFreshUninitializedClass() throws Exception {
        InitRecorder.reset();
        ClassLoader loader1 = demo.freshLoader(probeDir);
        ClassLoader loader2 = demo.freshLoader(probeDir);
        assertEquals(0, demo.loadClassOnly(loader1));
        // 第二个加载器里的同名类仍然是"未初始化"的。
        assertEquals(0, demo.forNameWithoutInit(loader2));
        assertEquals(0, InitRecorder.count());
        // 两个加载器加载出的是不同的 Class（类身份 = 全限定名 + 定义加载器）。
        assertFalse(loader1.loadClass(demo.PROBE_NAME) == loader2.loadClass(demo.PROBE_NAME));
    }
}