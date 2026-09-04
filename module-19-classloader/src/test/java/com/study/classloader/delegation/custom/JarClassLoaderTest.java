package com.study.classloader.delegation.custom;

import com.study.classloader.util.RuntimeCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JarClassLoader 测试：从真实 .jar 文件（JarFile）读取 .class 字节码加载类。
 *
 * 对应 FileClassLoaderTest 的 jar 版，并额外覆盖 jar 专有场景：
 * 多 jar 顺序搜索、同名类「先声明的 jar 先加载生效」、jar 与目录两种加载方式对照。
 * 原料链路：RuntimeCompiler.compile（目录）→ RuntimeCompiler.packageToJar（打成 jar）。
 */
class JarClassLoaderTest {

    /** 测试专用类：只在临时 jar 里存在，应用类路径上没有。 */
    private static final String TARGET_NAME = "com.study.classloader.delegation.custom.generated.JarClass";
    private static final String TARGET_SOURCE = """
            package com.study.classloader.delegation.custom.generated;

            /** 运行时编译生成、再打成 jar 的测试目标类。 */
            public class JarClass {
                public String who() {
                    return "loaded-by-" + getClass().getClassLoader().getClass().getSimpleName();
                }
            }
            """;

    /** 同名类的「第一个 jar 版本」。 */
    private static final String FIRST_SOURCE = """
            package com.study.classloader.delegation.custom.generated;

            public class JarClass {
                public String who() {
                    return "version-first";
                }
            }
            """;

    /** 同名类的「第二个 jar 版本」（行为不同）。 */
    private static final String SECOND_SOURCE = """
            package com.study.classloader.delegation.custom.generated;

            public class JarClass {
                public String who() {
                    return "version-second";
                }
            }
            """;

    @TempDir
    Path tempDir;

    /** 编译源码到临时目录并打成 jar，返回 jar 路径。 */
    private Path compileToJar(String sub, String source) throws Exception {
        Path classesDir = RuntimeCompiler.compile(tempDir.resolve(sub), TARGET_NAME, source);
        return RuntimeCompiler.packageToJar(classesDir, tempDir.resolve(sub + ".jar"));
    }

    @Test
    @DisplayName("从 jar 文件加载不在类路径上的类：由本加载器定义并正常执行")
    void loadsClassFromJar() throws Exception {
        Path jar = compileToJar("v1", TARGET_SOURCE);
        JarClassLoader loader = new JarClassLoader(jar, getClass().getClassLoader());

        Class<?> clazz = loader.loadClass(TARGET_NAME);
        assertEquals(TARGET_NAME, clazz.getName());
        assertSame(loader, clazz.getClassLoader(), "类应由定义它的加载器持有");
        assertEquals("loaded-by-JarClassLoader", invokeWho(clazz));
    }

    @Test
    @DisplayName("父优先委派：java.lang.String 由 Bootstrap 加载，与 JVM 内置的是同一份")
    void delegatesJdkClassesToParent() throws Exception {
        Path jar = compileToJar("v1", TARGET_SOURCE);
        JarClassLoader loader = new JarClassLoader(jar, getClass().getClassLoader());
        assertSame(String.class, loader.loadClass("java.lang.String"));
    }

    @Test
    @DisplayName("同一个加载器加载同名类只加载一次（findLoadedClass 缓存）")
    void sameLoaderCachesClass() throws Exception {
        Path jar = compileToJar("v1", TARGET_SOURCE);
        JarClassLoader loader = new JarClassLoader(jar, getClass().getClassLoader());
        Class<?> first = loader.loadClass(TARGET_NAME);
        Class<?> second = loader.loadClass(TARGET_NAME);
        assertSame(first, second, "同一加载器对同一全限定名应返回缓存的同一份 Class");
    }

    @Test
    @DisplayName("两个加载器加载同一个 jar：得到不同的 Class（类身份包含定义加载器）")
    void twoLoadersYieldDistinctClasses() throws Exception {
        Path jar = compileToJar("v1", TARGET_SOURCE);
        JarClassLoader loaderA = new JarClassLoader(jar, getClass().getClassLoader());
        JarClassLoader loaderB = new JarClassLoader(jar, getClass().getClassLoader());
        assertNotSame(loaderA.loadClass(TARGET_NAME), loaderB.loadClass(TARGET_NAME));
    }

    @Test
    @DisplayName("多 jar 顺序搜索：类只存在于第二个 jar 也能被找到")
    void searchesJarsInOrder() throws Exception {
        Path emptyJar = packageEmptyJar("empty.jar");
        Path jarWithClass = compileToJar("v1", TARGET_SOURCE);

        JarClassLoader loader = new JarClassLoader(List.of(emptyJar, jarWithClass), getClass().getClassLoader());
        Class<?> clazz = loader.loadClass(TARGET_NAME);
        assertSame(loader, clazz.getClassLoader(), "按 jar 顺序搜索，第二个 jar 里的类应被加载");
    }

    @Test
    @DisplayName("同名类在两个 jar 都有：先声明的 jar 生效（jar classpath 顺序 = 先到先得）")
    void jarOrderPreference() throws Exception {
        Path jarFirst = compileToJar("first", FIRST_SOURCE);
        Path jarSecond = compileToJar("second", SECOND_SOURCE);

        JarClassLoader loader = new JarClassLoader(List.of(jarFirst, jarSecond), getClass().getClassLoader());
        Class<?> clazz = loader.loadClass(TARGET_NAME);
        assertEquals("version-first", invokeWho(clazz), "同名类先声明的 jar 先加载生效");
    }

    @Test
    @DisplayName("找不到类时抛 ClassNotFoundException")
    void classNotFound() throws Exception {
        Path jar = compileToJar("v1", TARGET_SOURCE);
        JarClassLoader loader = new JarClassLoader(jar, getClass().getClassLoader());
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("com.study.classloader.nonexistent.NoSuch"));
    }

    @Test
    @DisplayName("listAvailableClasses 能列出 jar 里的所有类")
    void listsAvailableClasses() throws Exception {
        Path jar = compileToJar("v1", TARGET_SOURCE);
        JarClassLoader loader = new JarClassLoader(jar, getClass().getClassLoader());
        assertTrue(loader.listAvailableClasses().contains(TARGET_NAME),
                "应列出 jar 里的类: " + loader.listAvailableClasses());
    }

    @Test
    @DisplayName("jar 与目录加载同一个类：两种方式得到不同的 Class，但都能正常使用")
    void jarVsDirectoryDistinctIdentity() throws Exception {
        Path classesDir = RuntimeCompiler.compile(tempDir.resolve("classes"), TARGET_NAME, TARGET_SOURCE);
        Path jar = RuntimeCompiler.packageToJar(classesDir, tempDir.resolve("same.jar"));

        Class<?> fromJar = new JarClassLoader(jar, getClass().getClassLoader()).loadClass(TARGET_NAME);
        Class<?> fromDir = new FileClassLoader(classesDir, getClass().getClassLoader()).loadClass(TARGET_NAME);
        assertNotSame(fromJar, fromDir, "jar 与目录是两种加载方式，类身份不同");
        assertEquals("loaded-by-JarClassLoader", invokeWho(fromJar));
        assertEquals("loaded-by-FileClassLoader", invokeWho(fromDir));
    }

    private static String invokeWho(Class<?> clazz) throws Exception {
        return (String) clazz.getMethod("who").invoke(clazz.getDeclaredConstructor().newInstance());
    }

    private Path packageEmptyJar(String name) throws Exception {
        Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
        return RuntimeCompiler.packageToJar(emptyDir, tempDir.resolve(name));
    }
}