package com.study.classloader.delegation.custom;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 自定义类加载器：从真实的 .jar 文件（而不是解压目录）读取 .class 字节码加载类。
 *
 * 这是 {@code FileClassLoader} 的 jar 版（README「动手练习 1」的落地）：
 *  - FileClassLoader 传目录，用 Files.readAllBytes 读 .class 文件；
 *  - JarClassLoader 传 .jar 路径，用 {@link JarFile} 按 entry 名读取 .class 字节码。
 * 两者原理完全一致：jar 本质就是一个 zip 包，.class 以
 * {@code com/xxx/X.class} 的形式存放在 entry 里。
 *
 * 【同样遵循双亲委派】：没有重写 loadClass，只实现 findClass。
 * ClassLoader.loadClass 的标准流程（父优先）会：
 *  1. findLoadedClass(name) 检查本加载器是否已加载；
 *  2. 委派给父加载器（递归到 Bootstrap）；
 *  3. 父都找不到才调用本类的 findClass —— 在这里打开 jar、读 entry 并 defineClass。
 *
 * 与 FileClassLoader 的对照结论（见 JarClassLoaderDemo / JarClassLoaderTest）：
 *  - 同一个类，从 jar 加载 vs 从目录加载，得到的是两个不同的 Class
 *    （类身份 = 全限定名 + 定义它的加载器）；
 *  - 多个 jar 按顺序搜索，同名类「先声明的 jar 先加载生效」
 *    （就是真实 classpath / Maven 依赖仲裁的 jar 顺序规则）。
 *
 * 关键 API（务必记住）：
 *  - JarFile.getJarEntry(name)：按 entry 名精确取 entry（O(1) 查找）；
 *  - jarFile.getInputStream(entry).readAllBytes()：读 entry 里的字节码；
 *  - try-with-resources 关 JarFile：避免文件句柄泄漏（README「常见错误 7」）。
 *
 * 测试入口：{@code JarClassLoaderTest}
 */
public class JarClassLoader extends ClassLoader {

    /** 一个或多个 jar 文件路径，按顺序搜索，等价于 classpath 上的多个 jar。 */
    private final List<Path> jarFiles;

    public JarClassLoader(Path jarFile, ClassLoader parent) {
        this(List.of(jarFile), parent);
    }

    public JarClassLoader(List<Path> jarFiles, ClassLoader parent) {
        super(parent);
        this.jarFiles = new ArrayList<>(jarFiles);
    }

    /**
     * 双亲委派下的"自己动手"扩展点：
     * 按 jar 顺序查找 name 对应的 entry，读字节码并 defineClass。
     * 只有父加载器加载不到时才会被 loadClass 调用。
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // entry 名 = 全限定名的点换成斜杠 + .class 后缀，如 com/xxx/X.class。
        String entryName = name.replace('.', '/') + ".class";
        for (Path jar : jarFiles) {
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                JarEntry entry = jarFile.getJarEntry(entryName);
                if (entry != null) {
                    byte[] bytes = jarFile.getInputStream(entry).readAllBytes();
                    // 核心方法：把字节码变成 Class 对象，归属到本加载器名下。
                    return defineClass(name, bytes, 0, bytes.length);
                }
            } catch (IOException e) {
                throw new ClassNotFoundException(name + " 读取失败: " + jar, e);
            }
        }
        throw new ClassNotFoundException(name + " 不在任何 jar: " + jarFiles);
    }

    /**
     * 展示"本加载器负责加载了哪些类"（学习辅助，非标准 API）：
     * 遍历所有 jar 的 entry，列出 *.class 对应的类名（跳过 META-INF/ 下的辅助类）。
     */
    public List<String> listAvailableClasses() throws IOException {
        List<String> names = new ArrayList<>();
        for (Path jar : jarFiles) {
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    String entryName = entries.nextElement().getName();
                    if (entryName.endsWith(".class") && !entryName.startsWith("META-INF/")) {
                        names.add(entryName.substring(0, entryName.length() - ".class".length())
                                .replace('/', '.'));
                    }
                }
            }
        }
        return names;
    }
}