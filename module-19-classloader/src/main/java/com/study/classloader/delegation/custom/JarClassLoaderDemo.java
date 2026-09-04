package com.study.classloader.delegation.custom;

import com.study.classloader.util.RuntimeCompiler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * jar 文件加载演示（README「动手练习 1」的落地）：
 * 运行时把源码编译成目录 → 打成真实 .jar → 用 JarClassLoader 从 jar 里加载类并执行，
 * 再与 FileClassLoader 的目录加载对照：同一个类两种加载方式，类身份不同。
 *
 * 运行入口：Main 的第 9 个小节；测试入口：{@code JarClassLoaderTest}
 */
public class JarClassLoaderDemo {

    /** 演示类全限定名：只在临时 jar / 目录里存在，应用类路径上没有。 */
    public static final String TARGET_NAME = "com.study.classloader.delegation.custom.generated.JarGreeter";
    private static final String TARGET_SOURCE = """
            package com.study.classloader.delegation.custom.generated;

            /** 运行时编译生成、再打成 jar 的演示类。 */
            public class JarGreeter {
                public String hello() {
                    return "hello from " + getClass().getClassLoader().getClass().getSimpleName();
                }
            }
            """;

    public void demo() throws Exception {
        System.out.println("========== jar 文件加载：JarClassLoader 从真实 jar 读字节码 ==========");
        Path workDir = Files.createTempDirectory("jar-demo");
        Path classesDir = RuntimeCompiler.compile(workDir.resolve("classes"), TARGET_NAME, TARGET_SOURCE);
        Path jarFile = RuntimeCompiler.packageToJar(classesDir, workDir.resolve("plugin.jar"));
        System.out.println("已生成 jar：" + jarFile);

        // 1. 从真实 jar 加载并执行。
        JarClassLoader jarLoader = new JarClassLoader(jarFile, getClass().getClassLoader());
        Class<?> fromJar = jarLoader.loadClass(TARGET_NAME);
        System.out.println("jar 加载：" + invokeHello(fromJar));

        // 2. 对照：同一个类从解压目录加载。
        FileClassLoader dirLoader = new FileClassLoader(classesDir, getClass().getClassLoader());
        Class<?> fromDir = dirLoader.loadClass(TARGET_NAME);
        System.out.println("目录加载：" + invokeHello(fromDir));

        System.out.println("jar 与目录加载出的是同一个 Class 吗？" + (fromJar == fromDir));
        System.out.println();
        System.out.println("结论：jar 本质是 zip 包，JarFile 按 entry 名取 .class 字节码再 defineClass，");
        System.out.println("与目录加载原理一致；两种方式得到不同 Class（类身份 = 全限定名 + 加载器）。");
    }

    private static String invokeHello(Class<?> clazz) throws Exception {
        return (String) clazz.getMethod("hello").invoke(clazz.getDeclaredConstructor().newInstance());
    }
}