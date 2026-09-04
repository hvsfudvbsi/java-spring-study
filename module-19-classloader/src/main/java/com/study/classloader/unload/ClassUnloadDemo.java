package com.study.classloader.unload;

import com.study.classloader.delegation.custom.FileClassLoader;
import com.study.classloader.util.RuntimeCompiler;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 类卸载（Class Unloading）演示：验证 JVM 确实可以把不再使用的类回收掉。
 *
 * 类能被卸载的条件（三个都要满足，面试高频）：
 *  1. 该类的 Class 对象没有任何强引用（业务代码不再使用它）；
 *  2. 定义它的 ClassLoader 没有任何强引用（加载器本身也要"没人用"）；
 *  3. 该 ClassLoader 加载的【所有】类都满足条件 1（卸载是"整批"的，
 *     一个加载器下的类要么都活着、要么一起被卸载）。
 *
 * 怎么验证卸载：没有 API 能直接查询"类是否已卸载"，标准做法是用
 * WeakReference 持有 Class 对象，配合 System.gc() 循环回收后，
 * 若 weakRef.get() 变为 null，说明 Class（及其加载器）已被 GC 回收。
 *
 * 现实意义（回答"两个服务模块不同时使用能否靠加载/卸载机制"）：
 *  - 可以：每个模块用独立类加载器加载，模块用完把加载器引用全部置空，
 *    下次 GC 时模块的类随加载器一起被卸载，内存回收、互不污染；
 *  - 这正是 Tomcat reload（类加载器换新实现"热部署"）、Eclipse/IDEA 插件系统、
 *    OSGi 动态安装/卸载 bundle 的原理。
 *
 * 注意：类卸载发生在 GC 时，是否立即生效取决于垃圾回收器与内存压力，
 *  System.gc() 只是"请求"，演示里用循环 + 重试保证测试稳定。
 *
 * 测试入口：{@code ClassUnloadDemoTest}
 */
public class ClassUnloadDemo {

    /** 卸载目标类全限定名（运行时编译，应用类路径上【没有】它）。 */
    public static final String TARGET_NAME = "com.study.classloader.unload.generated.Unloadable";

    /** 卸载目标类源码：只有个静态字段和实例方法，方便确认"它能正常工作"。 */
    public static final String TARGET_SOURCE = """
            package com.study.classloader.unload.generated;

            /** 只存在于临时目录、可被卸载的目标类。 */
            public class Unloadable {
                public static final String MARK = "unloadable-ready";

                public String describe() {
                    return "loaded by: " + getClass().getClassLoader();
                }
            }
            """;

    /** 单次加载的观察结果。 */
    public record LoadResult(Class<?> clazz, WeakReference<Class<?>> weakRef, FileClassLoader loader) {
    }

    /**
     * 生成卸载目标类目录并返回：{@code [目标类所在目录]}。
     * 测试通过 @TempDir 调用。
     */
    public Path prepareTarget(Path workDir) throws IOException {
        return RuntimeCompiler.compile(workDir.resolve("unloadable"), TARGET_NAME, TARGET_SOURCE);
    }

    /**
     * 加载目标类并返回：{@code [Class, WeakReference, 定义它的加载器]}。
     * 此时类处于"被强引用"状态，weakRef.get() 一定非 null。
     */
    public LoadResult loadAndWatch(Path targetDir) throws Exception {
        FileClassLoader loader = new FileClassLoader(targetDir, getClass().getClassLoader());
        Class<?> clazz = loader.loadClass(TARGET_NAME);
        // 显式初始化（执行静态字段赋值），确保类进入"完整可用"状态。
        Class.forName(TARGET_NAME, true, loader);
        return new LoadResult(clazz, new WeakReference<>(clazz), loader);
    }

    /**
     * 触发卸载：循环 GC，等待 WeakReference 被清空（即 Class 已被回收、类已卸载）。
     *
     * 调用方必须【先】把强引用清掉：{@link LoadResult} 是 record（字段不可变），
     * 只能由调用方把持有它的变量置 null，再传入提前取出的弱引用。
     *
     * @param weakRef  提前取出的 {@code WeakReference<Class<?>>}
     * @param maxTries 最多尝试多少次（每次 System.gc() + 短暂等待）
     * @return 是否在限定次数内观察到类被卸载（weakRef.get() == null）
     */
    public boolean unloadAndWait(WeakReference<Class<?>> weakRef, int maxTries) throws InterruptedException {
        // 循环 GC：类卸载在 GC 阶段进行，无法精确控制时机，只能"请求 + 等待"。
        for (int i = 0; i < maxTries; i++) {
            System.gc();
            Thread.sleep(10);
            if (weakRef.get() == null) {
                return true; // WeakReference 被清空 = Class 已被回收 = 类已卸载
            }
        }
        return false;
    }

    /** 完整演示（运行入口用）：加载 → 卸载 → 验证，并再次加载证明"可重复"。 */
    public void demo() throws Exception {
        System.out.println("========== 类加载/卸载机制 ==========");
        Path workDir = Files.createTempDirectory("unload-demo");
        Path targetDir = prepareTarget(workDir);

        // 1. 第一次加载并使用。
        LoadResult first = loadAndWatch(targetDir);
        WeakReference<Class<?>> firstRef = first.weakRef();
        System.out.println("加载成功：" + first.clazz().getName()
                + "  加载器 = " + first.clazz().getClassLoader());
        System.out.println("  静态字段 MARK = " + first.clazz().getField("MARK").get(null));

        // 2. 卸载并验证（关键：先把强引用全部置空，只留弱引用）。
        first = null;
        boolean unloaded = unloadAndWait(firstRef, 50);
        System.out.println("清空强引用 + 循环 GC 后，类被卸载了吗？" + unloaded);

        // 3. 再次加载：证明"卸载后可以重新加载"，且得到全新的 Class（新加载器）。
        LoadResult second = loadAndWatch(targetDir);
        System.out.println("再次加载成功，加载器 = " + second.clazz().getClassLoader()
                + "，与第一次不是同一个 = " + (firstRef.get() == null));
        System.out.println();
        System.out.println("结论：类的生命周期 = 加载 → 使用 → （引用全清）→ GC 卸载；");
        System.out.println("两个服务模块不同时使用 → 各自独立加载器 + 用完卸载即可安全切换。");
    }
}