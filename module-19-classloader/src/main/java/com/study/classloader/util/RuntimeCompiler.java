package com.study.classloader.util;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * 运行时编译工具：把一段 Java 源码字符串编译成 .class 文件（javax.tools.JavaCompiler）。
 *
 * 用途（本模块的核心演示手段）：
 *  - 模拟"两个 jar 里有相同类名但内容不同"：把同名类源码编译到两个不同目录，
 *    每个目录等价于一个 jar 解压后的结构（jar 本质上就是 zip 包，.class 就在里面）；
 *  - 生成"不在应用类路径上"的类，供自定义类加载器加载/卸载演示；
 *  - {@link #packageToJar} 把编译好的目录再打成真实 .jar，供 JarClassLoader 演示。
 *
 * 为什么需要它：如果两个同名类都写进 src/main/java，Maven 编译时第二个会覆盖第一个，
 * 而且它们都会被 AppClassLoader 加载——就演示不了"隔离加载"和"类卸载"了。
 * 把其中一个（或全部）在运行时编译到临时目录，应用类路径上就没有它，
 * 自定义类加载器才能从目录里把它"找出来"。
 *
 * 关键 API：
 *  - ToolProvider.getSystemJavaCompiler()：拿 JDK 自带编译器（jdk.compiler 模块）；
 *  - SimpleJavaFileObject：让编译器"从内存字符串编译"而不用写 .java 文件；
 *  - fileManager.setLocation(CLASS_OUTPUT, ...)：指定 .class 输出目录。
 *
 * 测试入口：{@code com.study.classloader.conflict.IsolationDemoTest} 等各测试类。
 */
public final class RuntimeCompiler {

    private RuntimeCompiler() {
    }

    /**
     * 把单个类的源码字符串编译到 outputDir（自动创建目录）。
     *
     * @param outputDir  .class 输出目录（等价于一个 jar 的解压目录）
     * @param className  完整类名，例如 com.study.classloader.conflict.Greeter
     * @param source     该类的完整 Java 源码（必须与 className 一致）
     * @return outputDir，方便链式调用
     */
    public static Path compile(Path outputDir, String className, String source) throws IOException {
        return compileAll(outputDir, Map.of(className, source));
    }

    /**
     * 把多个类的源码一次性编译到 outputDir（自动创建目录）。
     * 多个类在同一个编译任务里，可以互相引用（例如访问器类引用探针类的静态字段）。
     *
     * @param outputDir .class 输出目录（等价于一个 jar 的解压目录）
     * @param sources   完整类名 -> 该类完整源码 的映射
     * @return outputDir，方便链式调用
     * @throws IllegalStateException 编译失败（源码有语法错误等），诊断信息会附在异常里
     */
    /**
     * 把编译好的 .class 目录打包成真实的 .jar 文件（zip 格式，.class 入口在包里）。
     *
     * 用途：{@code compile} 得到的是"jar 解压目录"，本方法把它还原成 jar，
     * 供 {@code JarClassLoader}（delegation.custom 包）从真实 .jar 文件加载类，
     * 等价于"把插件源码打成可发布的 jar"。
     *
     * @param compiledDir  compile 的输出目录（里面是包结构 + .class 文件）
     * @param jarFile      要生成的 jar 文件路径（父目录不存在会自动创建）
     * @return jarFile，方便链式调用
     */
    public static Path packageToJar(Path compiledDir, Path jarFile) throws IOException {
        if (jarFile.getParent() != null) {
            Files.createDirectories(jarFile.getParent());
        }
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            try (var stream = Files.walk(compiledDir)) {
                // 按名称排序保证打包结果稳定；entry 名用 / 分隔（zip 规范）。
                for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                    String entryName = compiledDir.relativize(file).toString().replace(File.separatorChar, '/');
                    jos.putNextEntry(new JarEntry(entryName));
                    jos.write(Files.readAllBytes(file));
                    jos.closeEntry();
                }
            }
        }
        return jarFile;
    }

    public static Path compileAll(Path outputDir, Map<String, String> sources) throws IOException {
        Files.createDirectories(outputDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("当前运行环境没有 jdk.compiler 模块（JRE 而非 JDK）");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            // 1. 指定 .class 输出目录：编译器会自动创建包目录（com/xxx/...）。
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));

            // 2. 把每个源码包成"内存中的 .java 文件"，避免落盘写源码。
            List<JavaFileObject> units = new ArrayList<>();
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String filePath = entry.getKey().replace('.', '/') + ".java";
                String source = entry.getValue();
                units.add(new SimpleJavaFileObject(URI.create("string:///" + filePath), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return source;
                    }
                });
            }

            // 3. 编译：-classpath 用进程当前 classpath，让源码能引用主代码里的接口类；
            //    -proc:none 关闭注解处理，避免误触发其他处理器。
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"), "-proc:none");
            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!success) {
                throw new IllegalStateException("源码编译失败: " + sources.keySet() + " -> " + diagnostics.getDiagnostics());
            }
        }
        return outputDir;
    }
}