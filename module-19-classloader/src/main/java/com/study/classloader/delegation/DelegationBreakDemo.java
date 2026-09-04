package com.study.classloader.delegation;

import com.study.classloader.delegation.custom.BreakDelegationClassLoader;
import com.study.classloader.delegation.custom.FileClassLoader;
import com.study.classloader.delegation.custom.TargetClass;
import com.study.classloader.util.RuntimeCompiler;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 双亲委派 vs 打破双亲委派 对照实验。
 *
 * 实验设计：
 *  - {@link TargetClass} 编译在 target/classes，已由 AppClassLoader 加载（父版本）；
 *  - 用 {@link RuntimeCompiler} 生成【同名】TargetClass（sayHello 返回 "child-version"）
 *    编译到临时目录（子版本）；
 *  - 两个自定义加载器从同一个临时目录加载同名类：
 *      FileClassLoader（遵循委派）      → 委派给父，父已加载 → 拿到【父版本】；
 *      BreakDelegationClassLoader（打破）→ 先自己目录找 → 拿到【子版本】。
 *
 * 观察重点：
 *  1. 同一个全限定名，在不同加载策略下解析到的 Class 对象【不是同一个】
 *     （类身份 = 全限定名 + 定义它的类加载器）；
 *  2. 跨加载器的两个 Class 无法直接强转/instanceof（会出现 ClassCastException），
 *     只能通过反射或共同接口交互——这正是"两个 jar 同名类"场景的根源问题。
 *
 * 测试入口：{@code com.study.classloader.delegation.custom.BreakDelegationClassLoaderTest}
 */
public class DelegationBreakDemo {

    /** 子版本源码：与 TargetClass 同名，但行为不同。 */
    public static final String CHILD_SOURCE = """
            package com.study.classloader.delegation.custom;

            /** 运行时编译生成的子版本 TargetClass（与 target/classes 里的同名不同内容）。 */
            public class TargetClass {
                public String sayHello() {
                    return "child-version";
                }
            }
            """;

    /** 目标类的全限定名（父版本与子版本共用）。 */
    public static final String TARGET_NAME = TargetClass.class.getName();

    /**
     * 生成子版本目录并返回：{@code [子版本.class 所在目录]}。
     * 测试会通过 @TempDir 调用。
     */
    public Path prepareChildVersion(Path workDir) throws IOException {
        Path childDir = workDir.resolve("child-version");
        return RuntimeCompiler.compile(childDir, TARGET_NAME, CHILD_SOURCE);
    }

    /**
     * 用"遵循双亲委派"的加载器加载同名类：应返回 AppClassLoader 已加载的父版本。
     *
     * @return [加载到的 Class, 它的定义加载器]
     */
    public Class<?> loadWithDelegation(Path childDir) throws ClassNotFoundException {
        FileClassLoader loader = new FileClassLoader(childDir, TargetClass.class.getClassLoader());
        return loader.loadClass(TARGET_NAME);
    }

    /**
     * 用"打破双亲委派"的加载器加载同名类：应返回临时目录里的子版本。
     *
     * @return [加载到的 Class, 它的定义加载器]
     */
    public Class<?> loadWithBreakDelegation(Path childDir) throws ClassNotFoundException {
        BreakDelegationClassLoader loader = new BreakDelegationClassLoader(
                childDir, "com.study.classloader.delegation.custom", TargetClass.class.getClassLoader());
        return loader.loadClass(TARGET_NAME);
    }

    /** 通过反射调用 sayHello()，演示跨加载器只能用反射/接口通信。 */
    public static String invokeSayHello(Class<?> targetClass) throws Exception {
        Method method = targetClass.getMethod("sayHello");
        return (String) method.invoke(targetClass.getDeclaredConstructor().newInstance());
    }

    /** 完整演示（运行入口用）：生成子版本、分别加载、对比行为与类身份。 */
    public void demo() throws Exception {
        System.out.println("========== 双亲委派 vs 打破双亲委派 ==========");
        Path workDir = Files.createTempDirectory("delegation-demo");
        Path childDir = prepareChildVersion(workDir);

        Class<?> delegation = loadWithDelegation(childDir);
        Class<?> breakDelegation = loadWithBreakDelegation(childDir);

        System.out.println("父版本(TargetClass.class)        sayHello() = " + invokeSayHello(TargetClass.class));
        System.out.println("FileClassLoader(遵循委派)          sayHello() = " + invokeSayHello(delegation)
                + "   加载器 = " + delegation.getClassLoader());
        System.out.println("BreakDelegationClassLoader(打破)  sayHello() = " + invokeSayHello(breakDelegation)
                + "   加载器 = " + breakDelegation.getClassLoader());
        System.out.println();
        System.out.println("委派版本 == 父版本 ? " + (delegation == TargetClass.class));
        System.out.println("打破版本 == 父版本 ? " + (breakDelegation == TargetClass.class));
        System.out.println("委派版本 == 打破版本 ? " + (delegation == breakDelegation));
        System.out.println();
        System.out.println("结论：全限定名相同但定义加载器不同 → 类身份不同，彼此 instanceof 不成立，" +
                "只能通过反射或共同接口交互。");
    }
}