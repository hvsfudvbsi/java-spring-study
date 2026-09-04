package com.study.classloader.spi;

import com.study.classloader.delegation.custom.FileClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程上下文类加载器（TCCL）演示测试：
 * 框架代码用 Class.forName 默认加载不到"第三方实现"（双亲委派看不到），
 * 设置 TCCL 后才能加载——这正是 JDBC DriverManager 加载驱动的机制。
 */
class TccLDemoTest {

    private final TccLDemo demo = new TccLDemo();

    @TempDir
    Path tempDir;

    private FileClassLoader child;

    @BeforeEach
    void setUp() throws Exception {
        Path implDir = demo.prepareImpl(tempDir);
        child = new FileClassLoader(implDir, Greeting.class.getClassLoader());
    }

    @Test
    @DisplayName("不用 TCCL：Class.forName 默认用调用者的类加载器，找不到实现类")
    void withoutTccLClassNotFound() {
        // Class.forName(String) 使用调用者的加载器（AppClassLoader），
        // 实现类只存在于 child（临时目录），AppClassLoader 看不见。
        assertThrows(ClassNotFoundException.class, () -> Class.forName(TccLDemo.IMPL_NAME));
    }

    @Test
    @DisplayName("用 TCCL：设置线程上下文类加载器后能加载实现类并 cast 成接口")
    void withTccLLoadsImpl() throws Exception {
        Greeting greeting = demo.loadViaTccL(child);
        assertEquals("hello from spi impl (loaded by TCCL)", greeting.greet());
    }

    @Test
    @DisplayName("TCCL 用错加载器（AppClassLoader）仍然找不到实现类")
    void wrongContextLoaderStillFails() {
        assertThrows(ClassNotFoundException.class,
                () -> demo.loadViaTccL(Greeting.class.getClassLoader()));
    }

    @Test
    @DisplayName("loadViaTccL 结束后恢复原 TCCL，不污染调用线程")
    void restoresOriginalTccL() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        demo.loadViaTccL(child);
        assertSame(original, Thread.currentThread().getContextClassLoader(),
                "finally 中应恢复 TCCL（线程池场景必须恢复，否则串线）");
    }

    @Test
    @DisplayName("实现类的定义加载器是 child，接口由父（AppClassLoader）提供")
    void implLoaderIsChildInterfaceIsParent() throws Exception {
        Greeting greeting = demo.loadViaTccL(child);
        Class<?> implClass = greeting.getClass();
        assertSame(child, implClass.getClassLoader(), "实现类应由 child 定义");
        assertTrue(Greeting.class.isAssignableFrom(implClass),
                "接口 Greeting 由父加载器提供，跨加载器也能 isAssignableFrom");
        assertSame(Greeting.class.getClassLoader(), ClassLoader.getSystemClassLoader());
    }
}