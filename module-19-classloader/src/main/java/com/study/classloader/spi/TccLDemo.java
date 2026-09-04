package com.study.classloader.spi;

import com.study.classloader.delegation.custom.FileClassLoader;
import com.study.classloader.util.RuntimeCompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 线程上下文类加载器（Thread Context ClassLoader，TCCL）与 SPI 演示。
 *
 * 背景问题（为什么需要 TCCL）：
 *  JDBC 的 DriverManager 位于 java.sql（平台模块），它要加载 MySQL/Oracle 等第三方驱动。
 *  双亲委派是"父优先"——平台类加载器看不到应用类路径上的第三方 jar；
 *  让第三方驱动上移（塞进平台模块）又违背隔离原则。于是 JDK 1.2 引入 TCCL 作为
 *  "反向通道"：框架代码用 {@code Thread.currentThread().getContextClassLoader()}
 *  拿到【发起调用线程】的类加载器（通常是 AppClassLoader），用它去加载实现类。
 *
 * 本 Demo 的模拟场景：
 *  - {@link Greeting} 接口在框架侧（本模块，AppClassLoader 加载）；
 *  - 实现类 SpiGreetingImpl 运行时编译到临时目录，只存在于自定义类加载器里，
 *    应用类路径上【没有】它（等价于第三方 jar 不在 classpath 上）；
 *  - 演示：不用 TCCL（默认用调用者 AppClassLoader）→ ClassNotFoundException；
 *    设置 TCCL 为自定义加载器 → 加载成功，还能 cast 成 Greeting 接口直接调用。
 *
 * 关键 API：
 *  - Thread.currentThread().getContextClassLoader() / setContextClassLoader(...)；
 *  - Class.forName(name, true, loader)：用指定加载器加载并初始化（true=初始化）。
 *
 * 测试入口：{@code TccLDemoTest}
 */
public class TccLDemo {

    /** 实现类全限定名（运行时编译生成，不在应用类路径上）。 */
    public static final String IMPL_NAME = "com.study.classloader.spi.impl.SpiGreetingImpl";

    /** 实现类源码：实现框架侧接口 Greeting。 */
    public static final String IMPL_SOURCE = """
            package com.study.classloader.spi.impl;

            import com.study.classloader.spi.Greeting;

            /** 运行时编译生成的"第三方实现"（模拟 MySQL Driver 这类第三方 jar 里的类）。 */
            public class SpiGreetingImpl implements Greeting {
                @Override
                public String greet() {
                    return "hello from spi impl (loaded by TCCL)";
                }
            }
            """;

    /**
     * 生成"第三方实现"目录并返回：{@code [实现类所在目录]}。
     * 测试通过 @TempDir 调用。
     */
    public Path prepareImpl(Path workDir) throws IOException {
        return RuntimeCompiler.compile(workDir.resolve("spi-impl"), IMPL_NAME, IMPL_SOURCE);
    }

    /**
     * 框架侧加载实现类的标准姿势（JDBC DriverManager 同款）：
     * 用线程上下文类加载器加载实现类并初始化、实例化，返回 Greeting 接口引用。
     *
     * @param contextLoader 要设置为线程上下文类加载器的加载器（应能看到实现类）
     */
    public Greeting loadViaTccL(ClassLoader contextLoader) throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            // 1. 关键：把线程上下文类加载器临时切到能看见实现的加载器。
            Thread.currentThread().setContextClassLoader(contextLoader);
            // 2. 用 TCCL 加载并初始化实现类（等价于 Class.forName(name) 但指定加载器）。
            Class<?> implClass = Class.forName(IMPL_NAME, true, contextLoader);
            // 3. 实例化：双亲委派下 Greeting 接口由父（AppClassLoader）解析，可直接强转。
            return (Greeting) implClass.getDeclaredConstructor().newInstance();
        } finally {
            // 4. 恢复原 TCCL，避免污染调用线程（线程池场景必须恢复，否则串线）。
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    /** 完整演示（运行入口用）：生成实现 → 展示"不用 TCCL 失败"与"用 TCCL 成功"。 */
    public void demo() throws Exception {
        System.out.println("========== SPI 与线程上下文类加载器 ==========");
        Path workDir = Files.createTempDirectory("tccl-demo");
        Path implDir = prepareImpl(workDir);
        FileClassLoader child = new FileClassLoader(implDir, Greeting.class.getClassLoader());

        System.out.println("实现类只存在于自定义加载器（模拟第三方 jar 不在 classpath）：");
        System.out.println("  应用类路径上有实现类吗？" + isOnAppClassPath(IMPL_NAME));
        System.out.println();
        System.out.println("【不用 TCCL】Class.forName 默认用调用者的 AppClassLoader：");
        try {
            Class.forName(IMPL_NAME);
            System.out.println("  竟然找到了？（不该发生）");
        } catch (ClassNotFoundException e) {
            System.out.println("  ClassNotFoundException ✓（AppClassLoader 看不见实现类）");
        }
        System.out.println();
        System.out.println("【用 TCCL】把线程上下文类加载器切到 child 再加载：");
        Greeting greeting = loadViaTccL(child);
        System.out.println("  加载成功，greet() = " + greeting.greet());
        System.out.println("  （JDBC 的 DriverManager 就是这样加载 mysql-connector 的）");
    }

    private static boolean isOnAppClassPath(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}