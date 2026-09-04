package com.study.classloader.conflict;

import com.study.classloader.delegation.custom.FileClassLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 隔离加载演示：两个 jar 里有同名类，但【两个都要用】——让它们共存。
 *
 * 核心思想（也是 Tomcat / OSGi / Spring Boot fat jar 的做法）：
 *  类身份 = 全限定名 + 定义它的类加载器。
 *  只要用【两个不同的类加载器】分别加载两个 jar，同名类就是两个不同的 Class，
 *  互不覆盖、互不干扰，业务侧通过共同接口（{@link IGreeter}，父加载器提供）调用。
 *
 * 与 {@link ClassConflictDemo} 的对比：
 *  - 冲突场景：两个 jar 的类塞进【同一个】类加载器 → 只有先加载的生效；
 *  - 隔离场景：每个 jar 配【一个专属】类加载器 → 两个版本同时可用。
 *
 * 适用/不适用：
 *  - 适用：插件系统、多版本共存、按需加载的模块（两个服务模块不同时用）；
 *  - 不适用：全局单例类（Spring 容器、日志绑定）被双份加载会导致
 *    状态分裂 / ClassCastException —— 这类"全局设施"必须靠 Maven 仲裁收敛成一份。
 *
 * 测试入口：{@code IsolationDemoTest}
 */
public class IsolationDemo {

    /** 同 {@link ClassConflictDemo}：两个 jar 里都有同名 Greeter。 */
    public static final String GREETER_NAME = ClassConflictDemo.GREETER_NAME;

    /**
     * 用两个独立类加载器分别加载两个 jar 的同名类，都 cast 成 IGreeter 返回。
     *
     * @param jarA jarA 解压目录
     * @param jarB jarB 解压目录
     * @return [jarA 的 Greeter, jarB 的 Greeter, jarA 的 Class, jarB 的 Class]
     */
    public IsolatedPair loadIsolated(Path jarA, Path jarB) throws Exception {
        // 1. 两个专属加载器：父都是接口所在的 AppClassLoader（保证接口是同一份）。
        FileClassLoader loaderA = new FileClassLoader(jarA, IGreeter.class.getClassLoader());
        FileClassLoader loaderB = new FileClassLoader(jarB, IGreeter.class.getClassLoader());

        // 2. 各自加载同名类：由于目录不同、加载器不同，得到两个不同的 Class。
        Class<?> classA = loaderA.loadClass(GREETER_NAME);
        Class<?> classB = loaderB.loadClass(GREETER_NAME);

        // 3. 兼容层：父加载器提供的 IGreeter 接口 → 两个版本都能直接强转，无需反射。
        IGreeter greeterA = (IGreeter) classA.getDeclaredConstructor().newInstance();
        IGreeter greeterB = (IGreeter) classB.getDeclaredConstructor().newInstance();
        return new IsolatedPair(greeterA, greeterB, classA, classB);
    }

    /**
     * 隔离加载的结果封装：两个版本的接口引用 + 两个 Class（用于断言类身份不同）。
     */
    public record IsolatedPair(IGreeter a, IGreeter b, Class<?> classA, Class<?> classB) {
    }

    /** 完整演示（运行入口用）：两个同名类同时加载、同时调用。 */
    public void demo() throws Exception {
        System.out.println("========== 隔离加载：两个 jar 同名类共存 ==========");
        Path workDir = Files.createTempDirectory("isolation-demo");
        ClassConflictDemo conflict = new ClassConflictDemo();
        List<Path> jars = conflict.prepareTwoJars(workDir);

        IsolatedPair pair = loadIsolated(jars.get(0), jars.get(1));
        System.out.println("jarA 的 Greeter.hello() = " + pair.a().hello());
        System.out.println("jarB 的 Greeter.hello() = " + pair.b().hello());
        System.out.println("两个 Class 是同一个吗？" + (pair.classA() == pair.classB()));
        System.out.println("jarA 的类加载器: " + pair.classA().getClassLoader());
        System.out.println("jarB 的类加载器: " + pair.classB().getClassLoader());
        System.out.println();
        System.out.println("结论：同名类在不同加载器里是不同 Class，可同时使用；");
        System.out.println("通过父加载器提供的共同接口 IGreeter 直接调用，兼顾类型安全与兼容。");
    }
}