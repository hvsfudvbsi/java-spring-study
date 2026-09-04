package com.study.classloader.conflict;

import com.study.classloader.delegation.custom.FileClassLoader;
import com.study.classloader.util.RuntimeCompiler;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 类冲突（Jar Hell）演示：同一个类加载器里，同名类只有一份，谁先加载谁生效。
 *
 * 场景还原：
 *  项目同时依赖了两个 jar，它们都包含 {@code com.study.classloader.conflict.Greeter}
 *  这个类，但实现不同（version-A / version-B）——这正是面试题
 *  "两个 jar 存在相同内容（同名类）怎么办" 的第一步：先搞清楚为什么会冲突。
 *
 * 关键结论：
 *  1. 同一个类加载器对同一个全限定名最多加载一次（findLoadedClass 缓存）；
 *  2. 类加载器按 classpath/搜索目录【顺序】查找，先找到的先加载、后找到的忽略；
 *  3. 后果：业务代码引用的是"先加载的那个版本"，另一个版本的逻辑完全不会执行，
 *     且运行时可能因方法签名不同抛 NoSuchMethodError / AbstractMethodError 等。
 *
 * 现实中的同类场景：
 *  - Maven 依赖仲裁：不同传递依赖引入同一坐标的不同版本，Maven 按
 *    "最近路径优先、同深度先声明优先" 只保留一个版本（dependencyManagement 可强制指定）；
 *  - classpath 顺序：直接 -cp a.jar:b.jar 时，a.jar 在前则 a.jar 里的类生效；
 *  - 解决"只有一个能生效"：排除（exclusion）、升级对齐版本、或用
 *    {@link IsolationDemo} 的隔离加载让两个版本共存。
 *
 * 测试入口：{@code ClassConflictDemoTest}
 */
public class ClassConflictDemo {

    /** 冲突类的全限定名（两个"jar"里都有它，内容不同）。 */
    public static final String GREETER_NAME = "com.study.classloader.conflict.Greeter";

    /** version-A 源码（对应 jarA 里的 Greeter）。 */
    public static final String VERSION_A_SOURCE = """
            package com.study.classloader.conflict;

            /** jarA 里的 Greeter：hello() 返回 version-A。 */
            public class Greeter implements IGreeter {
                @Override
                public String hello() {
                    return "version-A";
                }
            }
            """;

    /** version-B 源码（对应 jarB 里的 Greeter，与 A 同名同接口、不同实现）。 */
    public static final String VERSION_B_SOURCE = """
            package com.study.classloader.conflict;

            /** jarB 里的 Greeter：hello() 返回 version-B。 */
            public class Greeter implements IGreeter {
                @Override
                public String hello() {
                    return "version-B";
                }
            }
            """;

    /**
     * 准备"两个 jar"的解压目录并返回：{@code [jarA目录, jarB目录]}。
     * 测试通过 @TempDir 调用。
     */
    public List<Path> prepareTwoJars(Path workDir) throws IOException {
        Path jarA = RuntimeCompiler.compile(workDir.resolve("jarA"), GREETER_NAME, VERSION_A_SOURCE);
        Path jarB = RuntimeCompiler.compile(workDir.resolve("jarB"), GREETER_NAME, VERSION_B_SOURCE);
        return List.of(jarA, jarB);
    }

    /**
     * 模拟"classpath 顺序"：同一个 FileClassLoader 按目录顺序查找同名类，
     * 先找到的先加载。返回 hello() 的结果。
     *
     * @param searchDirs 目录顺序 = classpath 顺序
     */
    public String helloFromFirstMatch(List<Path> searchDirs) throws Exception {
        FileClassLoader loader = new FileClassLoader(searchDirs, IGreeter.class.getClassLoader());
        // 同名类：目录顺序在前者胜出，后者被"看不见"。
        Class<?> greeterClass = loader.loadClass(GREETER_NAME);
        Method hello = greeterClass.getMethod("hello");
        return (String) hello.invoke(greeterClass.getDeclaredConstructor().newInstance());
    }

    /**
     * 完整演示（运行入口用）：生成两个"jar"，用不同 classpath 顺序观察谁生效。
     */
    public void demo() throws Exception {
        System.out.println("========== 类冲突：同一个类加载器里同名类先加载生效 ==========");
        Path workDir = Files.createTempDirectory("conflict-demo");
        List<Path> jars = prepareTwoJars(workDir);

        System.out.println("jarA 在前（jarA:jarB）→ hello() = " + helloFromFirstMatch(List.of(jars.get(0), jars.get(1))));
        System.out.println("jarB 在前（jarB:jarA）→ hello() = " + helloFromFirstMatch(List.of(jars.get(1), jars.get(0))));
        System.out.println();
        System.out.println("结论：同一加载器内同名类只能有一份 → 要么仲裁选一个，");
        System.out.println("要么用隔离类加载器（见 IsolationDemo）让两个版本共存。");
    }
}