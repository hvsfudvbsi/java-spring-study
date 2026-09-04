package com.study.classloader.delegation.custom;

import com.study.classloader.util.RuntimeCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义类加载器（遵循双亲委派）：验证 findClass/defineClass、父优先委派、缓存与多目录搜索。
 */
class FileClassLoaderTest {

    /** 测试专用类：只在临时目录里存在，应用类路径上没有。 */
    private static final String TARGET_NAME = "com.study.classloader.delegation.custom.generated.DiskClass";
    private static final String TARGET_SOURCE = """
            package com.study.classloader.delegation.custom.generated;

            /** 运行时编译生成的测试目标类。 */
            public class DiskClass {
                public String who() {
                    return "loaded-by-" + getClass().getClassLoader().getClass().getSimpleName();
                }
            }
            """;

    @TempDir
    Path tempDir;

    private Path compileTarget(Path dir) throws Exception {
        return RuntimeCompiler.compile(dir, TARGET_NAME, TARGET_SOURCE);
    }

    @Test
    @DisplayName("从目录加载不在类路径上的类：由本加载器定义")
    void loadsClassFromDirectory() throws Exception {
        Path dir = compileTarget(tempDir.resolve("classes"));
        FileClassLoader loader = new FileClassLoader(dir, getClass().getClassLoader());

        Class<?> clazz = loader.loadClass(TARGET_NAME);
        assertEquals(TARGET_NAME, clazz.getName());
        assertSame(loader, clazz.getClassLoader(), "类应由定义它的加载器持有");
        assertEquals("loaded-by-FileClassLoader", invokeWho(clazz));
    }

    @Test
    @DisplayName("父优先委派：java.lang.String 由 Bootstrap 加载，与 JVM 内置的是同一份")
    void delegatesJdkClassesToParent() throws Exception {
        Path dir = compileTarget(tempDir.resolve("classes"));
        FileClassLoader loader = new FileClassLoader(dir, getClass().getClassLoader());
        assertSame(String.class, loader.loadClass("java.lang.String"));
    }

    @Test
    @DisplayName("同一个加载器加载同名类只加载一次（findLoadedClass 缓存）")
    void sameLoaderCachesClass() throws Exception {
        Path dir = compileTarget(tempDir.resolve("classes"));
        FileClassLoader loader = new FileClassLoader(dir, getClass().getClassLoader());
        Class<?> first = loader.loadClass(TARGET_NAME);
        Class<?> second = loader.loadClass(TARGET_NAME);
        assertSame(first, second, "同一加载器对同一全限定名应返回缓存的同一份 Class");
    }

    @Test
    @DisplayName("两个加载器加载同一目录：得到不同的 Class（类身份包含定义加载器）")
    void twoLoadersYieldDistinctClasses() throws Exception {
        Path dir = compileTarget(tempDir.resolve("classes"));
        FileClassLoader loaderA = new FileClassLoader(dir, getClass().getClassLoader());
        FileClassLoader loaderB = new FileClassLoader(dir, getClass().getClassLoader());
        assertNotSame(loaderA.loadClass(TARGET_NAME), loaderB.loadClass(TARGET_NAME));
    }

    @Test
    @DisplayName("多目录搜索：类只存在于第二个目录也能被找到")
    void searchesDirectoriesInOrder() throws Exception {
        Path dirA = compileTarget(tempDir.resolve("a")); // 编译到 a，但类在 b 里才有
        Path dirB = compileTarget(tempDir.resolve("b"));
        // 删掉 a 里的 .class，让类只存在于 b。
        deleteClassFiles(dirA, TARGET_NAME);

        FileClassLoader loader = new FileClassLoader(List.of(dirA, dirB), getClass().getClassLoader());
        Class<?> clazz = loader.loadClass(TARGET_NAME);
        assertSame(loader, clazz.getClassLoader(), "按目录顺序搜索，b 里的类应被加载");
    }

    @Test
    @DisplayName("找不到类时抛 ClassNotFoundException")
    void classNotFound() throws Exception {
        Path dir = compileTarget(tempDir.resolve("classes"));
        FileClassLoader loader = new FileClassLoader(dir, getClass().getClassLoader());
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("com.study.classloader.nonexistent.NoSuch"));
    }

    @Test
    @DisplayName("listAvailableClasses 能列出目录里的所有类")
    void listsAvailableClasses() throws Exception {
        Path dir = compileTarget(tempDir.resolve("classes"));
        FileClassLoader loader = new FileClassLoader(dir, getClass().getClassLoader());
        List<String> available = loader.listAvailableClasses();
        assertTrue(available.contains(TARGET_NAME), "应列出编译出的类: " + available);
    }

    private static String invokeWho(Class<?> clazz) throws Exception {
        return (String) clazz.getMethod("who").invoke(clazz.getDeclaredConstructor().newInstance());
    }

    private static void deleteClassFiles(Path root, String className) throws Exception {
        Path file = root.resolve(className.replace('.', '/') + ".class");
        java.nio.file.Files.deleteIfExists(file);
    }
}