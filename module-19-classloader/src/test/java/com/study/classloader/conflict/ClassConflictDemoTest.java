package com.study.classloader.conflict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 类冲突（Jar Hell）：同一个类加载器里同名类只有一份，先加载的生效，
 * classpath/搜索目录顺序决定谁被加载。
 */
class ClassConflictDemoTest {

    private final ClassConflictDemo demo = new ClassConflictDemo();

    @TempDir
    Path tempDir;

    private Path jarA;
    private Path jarB;

    @BeforeEach
    void setUp() throws Exception {
        List<Path> jars = demo.prepareTwoJars(tempDir);
        jarA = jars.get(0);
        jarB = jars.get(1);
    }

    @Test
    @DisplayName("jarA 在前：同名 Greeter 用 jarA 的版本")
    void jarAFirstWins() throws Exception {
        assertEquals("version-A", demo.helloFromFirstMatch(List.of(jarA, jarB)));
    }

    @Test
    @DisplayName("jarB 在前：同名 Greeter 用 jarB 的版本")
    void jarBFirstWins() throws Exception {
        assertEquals("version-B", demo.helloFromFirstMatch(List.of(jarB, jarA)));
    }

    @Test
    @DisplayName("同一加载器只保留一份同名类：重复加载返回同一份 Class")
    void sameLoaderKeepsSingleCopy() throws Exception {
        com.study.classloader.delegation.custom.FileClassLoader loader =
                new com.study.classloader.delegation.custom.FileClassLoader(
                        List.of(jarA, jarB), IGreeter.class.getClassLoader());
        Class<?> first = loader.loadClass(ClassConflictDemo.GREETER_NAME);
        Class<?> second = loader.loadClass(ClassConflictDemo.GREETER_NAME);
        assertSame(first, second, "findLoadedClass 缓存：后加载的版本被忽略");
    }

    @Test
    @DisplayName("先加载的版本实现了共同接口 IGreeter（父加载器提供），可安全调用")
    void winnerImplementsSharedInterface() throws Exception {
        Class<?> greeterClass = new com.study.classloader.delegation.custom.FileClassLoader(
                List.of(jarA, jarB), IGreeter.class.getClassLoader())
                .loadClass(ClassConflictDemo.GREETER_NAME);
        // 共同接口由父（AppClassLoader）加载，两个版本都实现它 → 可直接强转。
        IGreeter greeter = (IGreeter) greeterClass.getDeclaredConstructor().newInstance();
        assertEquals("version-A", greeter.hello());
    }
}