package com.study.classloader.conflict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隔离加载：两个 jar 有同名类但【都要用】时，用两个独立类加载器各加载一份，
 * 通过父加载器提供的共同接口 IGreeter 调用——两个版本同时共存、互不干扰。
 */
class IsolationDemoTest {

    private final IsolationDemo demo = new IsolationDemo();

    @TempDir
    Path tempDir;

    private Path jarA;
    private Path jarB;

    @BeforeEach
    void setUp() throws Exception {
        List<Path> jars = new ClassConflictDemo().prepareTwoJars(tempDir);
        jarA = jars.get(0);
        jarB = jars.get(1);
    }

    @Test
    @DisplayName("两个同名类同时加载：各自返回自己的版本，互不覆盖")
    void bothVersionsUsable() throws Exception {
        IsolationDemo.IsolatedPair pair = demo.loadIsolated(jarA, jarB);
        assertEquals("version-A", pair.a().hello());
        assertEquals("version-B", pair.b().hello());
    }

    @Test
    @DisplayName("同名类的 Class 不是同一个（类身份 = 全限定名 + 定义加载器）")
    void classIdentityDiffers() throws Exception {
        IsolationDemo.IsolatedPair pair = demo.loadIsolated(jarA, jarB);
        assertNotSame(pair.classA(), pair.classB());
        assertFalse(pair.classA() == pair.classB());
        assertFalse(pair.classA().getClassLoader() == pair.classB().getClassLoader(),
                "两个版本应由不同加载器定义");
    }

    @Test
    @DisplayName("跨加载器不能 instanceof：classA 的实例不是 classB 的实例")
    void crossLoaderInstanceofFails() throws Exception {
        IsolationDemo.IsolatedPair pair = demo.loadIsolated(jarA, jarB);
        // 类身份不同 → 一个加载器加载的实例，对另一个加载器的 Class 不成立 instanceof。
        assertFalse(pair.classB().isInstance(pair.a()));
        assertFalse(pair.classA().isInstance(pair.b()));
    }

    @Test
    @DisplayName("共同接口由父加载器提供：两个版本都可直接 cast，无需反射")
    void sharedInterfaceFromParent() throws Exception {
        IsolationDemo.IsolatedPair pair = demo.loadIsolated(jarA, jarB);
        assertTrue(IGreeter.class.isAssignableFrom(pair.classA()));
        assertTrue(IGreeter.class.isAssignableFrom(pair.classB()));
        // 接口是同一份（AppClassLoader 加载），业务代码类型安全地同时使用两个版本。
        assertEquals("version-A version-B",
                pair.a().hello() + " " + pair.b().hello());
    }
}