package com.study.classloader.delegation.custom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义类加载器：从磁盘目录（模拟"一个 jar 解压后的目录"）读取 .class 字节码加载类。
 *
 * 本类【遵循双亲委派】：没有重写 loadClass，只实现 findClass。
 * ClassLoader.loadClass 的标准流程（父优先）会：
 *  1. findLoadedClass(name) 检查本加载器是否已加载；
 *  2. 委派给父加载器（递归到 Bootstrap）；
 *  3. 父都找不到才调用本类的 findClass —— 在这里读目录里的 .class 文件并 defineClass。
 *
 * 用途：
 *  - 加载"不在应用类路径上"的类（例如运行时编译到临时目录的插件实现类）；
 *  - 同一个目录内容可以被多个 FileClassLoader 各加载一份 → 同名类互不干扰（类隔离）。
 *
 * 关键 API（务必记住）：
 *  - findClass(String)：ClassLoader 留给子类的扩展点，找到字节码后必须调 defineClass；
 *  - defineClass(name, bytes, off, len)：把字节码变成 Class 对象（protected，只能子类调用）；
 *  - 只重写 findClass 而【不】重写 loadClass → 双亲委派默认生效。
 *
 * 测试入口：{@code FileClassLoaderTest}
 */
public class FileClassLoader extends ClassLoader {

    /** 一个或多个"类根目录"，按顺序搜索，等价于多个 jar 的 classpath 顺序。 */
    private final List<Path> baseDirs;

    public FileClassLoader(Path baseDir, ClassLoader parent) {
        this(List.of(baseDir), parent);
    }

    public FileClassLoader(List<Path> baseDirs, ClassLoader parent) {
        super(parent);
        this.baseDirs = new ArrayList<>(baseDirs);
    }

    /**
     * 双亲委派下的"自己动手"扩展点：
     * 按目录顺序查找 name 对应的 .class 文件，读字节码并 defineClass。
     * 只有父加载器加载不到时才会被 loadClass 调用。
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (Path dir : baseDirs) {
            Path file = dir.resolve(name.replace('.', '/') + ".class");
            if (Files.isRegularFile(file)) {
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    // 核心方法：把字节码变成 Class 对象，归属到本加载器名下。
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name + " 读取失败: " + file, e);
                }
            }
        }
        throw new ClassNotFoundException(name + " 不在任何目录: " + baseDirs);
    }

    /**
     * 展示"本加载器负责加载了哪些类"（学习辅助，非标准 API）：
     * 遍历所有目录，列出能找到的 .class 对应的类名。
     */
    public List<String> listAvailableClasses() throws IOException {
        List<String> names = new ArrayList<>();
        for (Path dir : baseDirs) {
            try (var stream = Files.walk(dir)) {
                stream.filter(p -> p.toString().endsWith(".class"))
                        .map(p -> dir.relativize(p).toString())
                        .map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.'))
                        .forEach(names::add);
            }
        }
        return names;
    }
}