package com.study.classloader.plugin;

import com.study.classloader.delegation.custom.FileClassLoader;
import com.study.classloader.util.RuntimeCompiler;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 插件运行器：演示"两个同名插件版本，不同时使用，靠类的加载/卸载机制安全切换"。
 *
 * 这正是用户问题的完整落地：
 *  两个服务模块（plugin-v1 / plugin-v2）打包后都包含同名类
 *  {@code com.study.classloader.plugin.generated.GreetingPlugin}，且不会同时使用。
 *  → 每次只加载需要的那一个：用完把类加载器引用全部置空，GC 卸载旧版本，
 *    需要切换时用全新加载器加载另一个版本，两者互不残留。
 *
 * 卸载前先调用 {@link PluginVersion#shutdown()} 钩子释放资源（README 动手练习 2），
 * 再清空引用交给 GC 回收。
 *
 * 与"同时使用"（IsolationDemo）的区别：
 *  - 同时使用：两个加载器并存，各自加载各自版本（内存里有双份类）；
 *  - 不同时使用：同一时刻只有一个加载器存活，切换时旧类被卸载（内存更省、更干净）。
 *
 * 现实对应：Tomcat 的 reload、Spring Boot DevTools 重启、OSGi 动态换 bundle、
 *  Arthas/JVM 插件热加载，本质都是"新类加载器 + 丢弃旧加载器"。
 *
 * 测试入口：{@code PluginRunnerTest}
 */
public class PluginRunner {

    /** 插件实现类全限定名：两个"服务模块"jar 里都有它（同名）。 */
    public static final String PLUGIN_NAME = "com.study.classloader.plugin.generated.GreetingPlugin";

    /** plugin-v1 源码（模块 v1 里的同名实现类）。 */
    public static final String V1_SOURCE = """
            package com.study.classloader.plugin.generated;

            import com.study.classloader.plugin.PluginVersion;

            /** 服务模块 v1 的实现类。 */
            public class GreetingPlugin implements PluginVersion {
                /** 资源是否已释放（卸载钩子是否被调用）。 */
                private boolean shutDown = false;

                @Override
                public String name() {
                    return "plugin-v1";
                }

                @Override
                public String execute(String input) {
                    return "[v1] processed: " + input;
                }

                /** 卸载钩子：释放模块持有的资源（模拟关闭连接/线程池）。 */
                @Override
                public void shutdown() {
                    shutDown = true;
                    System.out.println("    [plugin-v1] shutdown()：已释放连接与线程池资源");
                }

                /** 仅供测试检查：shutdown() 是否已被调用。 */
                public boolean isShutDown() {
                    return shutDown;
                }
            }
            """;

    /** plugin-v2 源码（模块 v2 里的同名实现类，行为不同）。 */
    public static final String V2_SOURCE = """
            package com.study.classloader.plugin.generated;

            import com.study.classloader.plugin.PluginVersion;

            /** 服务模块 v2 的实现类。 */
            public class GreetingPlugin implements PluginVersion {
                /** 资源是否已释放（卸载钩子是否被调用）。 */
                private boolean shutDown = false;

                @Override
                public String name() {
                    return "plugin-v2";
                }

                @Override
                public String execute(String input) {
                    return "[v2] processed: " + input.toUpperCase();
                }

                /** 卸载钩子：释放模块持有的资源（模拟关闭连接/线程池）。 */
                @Override
                public void shutdown() {
                    shutDown = true;
                    System.out.println("    [plugin-v2] shutdown()：已释放连接与线程池资源");
                }

                /** 仅供测试检查：shutdown() 是否已被调用。 */
                public boolean isShutDown() {
                    return shutDown;
                }
            }
            """;

    /**
     * 已加载插件的句柄：持有接口引用、Class、加载器三个强引用。
     * 调用 {@link #unload} 后全部置空，插件即进入"可被 GC 卸载"状态。
     */
    public static final class PluginHandle {
        private PluginVersion plugin;
        private Class<?> clazz;
        private FileClassLoader loader;
        private final WeakReference<Class<?>> weakRef;

        private PluginHandle(PluginVersion plugin, Class<?> clazz, FileClassLoader loader) {
            this.plugin = plugin;
            this.clazz = clazz;
            this.loader = loader;
            this.weakRef = new WeakReference<>(clazz);
        }

        /** 通过共同接口调用（无需反射）。 */
        public String execute(String input) {
            return plugin.execute(input);
        }

        public String name() {
            return plugin.name();
        }

        /**
         * 插件实例（仅供测试/演示检查用）。
         * ⚠️ 注意：外部持有它会阻止类被 GC 卸载（README 常见错误 5），
         * 检查完必须释放引用才能验证卸载。
         */
        public PluginVersion plugin() {
            return plugin;
        }

        /** 弱引用：用于验证类是否已被卸载（get() == null 说明已卸载）。 */
        public WeakReference<Class<?>> weakRef() {
            return weakRef;
        }
    }

    /**
     * 准备两个版本的"模块 jar"（解压目录）并返回：{@code [v1目录, v2目录]}。
     * 测试通过 @TempDir 调用。
     */
    public Path[] preparePlugins(Path workDir) throws IOException {
        Path v1 = RuntimeCompiler.compile(workDir.resolve("module-v1"), PLUGIN_NAME, V1_SOURCE);
        Path v2 = RuntimeCompiler.compile(workDir.resolve("module-v2"), PLUGIN_NAME, V2_SOURCE);
        return new Path[]{v1, v2};
    }

    /**
     * 加载指定目录下的插件版本（等价于"部署并启动模块"）。
     *
     * @param pluginDir 某个版本的"jar 解压目录"
     * @return 插件句柄；父加载器取 PluginVersion 接口的加载器，保证接口是同一份
     */
    public PluginHandle load(Path pluginDir) throws Exception {
        FileClassLoader loader = new FileClassLoader(pluginDir, PluginVersion.class.getClassLoader());
        Class<?> clazz = loader.loadClass(PLUGIN_NAME);
        // 初始化（执行静态代码），并实例化 + 强转成共同接口。
        PluginVersion plugin = (PluginVersion) clazz.getDeclaredConstructor().newInstance();
        return new PluginHandle(plugin, clazz, loader);
    }

    /**
     * 卸载插件（等价于"停掉模块"）：
     * 1. 先调用卸载钩子 {@link PluginVersion#shutdown()} 释放资源；
     * 2. 再清空所有强引用——之后只要没有别处还持有 Class / 加载器引用，
     *    GC 即可卸载整个模块的类。
     */
    public void unload(PluginHandle handle) {
        if (handle.plugin != null) {
            handle.plugin.shutdown(); // 卸载钩子：先释放模块资源，再清引用
        }
        handle.plugin = null;
        handle.clazz = null;
        handle.loader = null;
    }

    /**
     * 卸载并等待回收：清空引用 + 循环 GC，直到弱引用被清空（类被卸载）。
     *
     * @return 是否在 maxTries 次内观察到卸载
     */
    public boolean unloadAndWaitGc(PluginHandle handle, int maxTries) throws InterruptedException {
        unload(handle);
        for (int i = 0; i < maxTries; i++) {
            System.gc();
            Thread.sleep(10);
            if (handle.weakRef().get() == null) {
                return true;
            }
        }
        return false;
    }

    /** 完整演示（运行入口用）：v1 加载 → 使用 → 卸载 → v2 加载 → 使用 → 卸载。 */
    public void demo() throws Exception {
        System.out.println("========== 插件系统：两个同名模块不同时使用，加载/卸载切换 ==========");
        Path workDir = Files.createTempDirectory("plugin-demo");
        Path[] dirs = preparePlugins(workDir);

        // 1. 部署 v1 并使用。
        PluginHandle v1 = load(dirs[0]);
        System.out.println("部署 v1：" + v1.name() + " → " + v1.execute("hello"));
        boolean v1Unloaded = unloadAndWaitGc(v1, 50);
        System.out.println("停用 v1 并回收：" + v1Unloaded);

        // 2. 部署 v2 并使用（此时 v1 的类已被卸载，v2 是全新的类）。
        PluginHandle v2 = load(dirs[1]);
        System.out.println("部署 v2：" + v2.name() + " → " + v2.execute("hello"));
        System.out.println("v1 的类已被 GC 卸载（弱引用已清空）？" + (v1.weakRef().get() == null));
        boolean v2Unloaded = unloadAndWaitGc(v2, 50);
        System.out.println("停用 v2 并回收：" + v2Unloaded);

        // 3. 需要时还能再部署 v1（加载/卸载可反复进行）。
        PluginHandle v1Again = load(dirs[0]);
        System.out.println("再次部署 v1：" + v1Again.name() + " → " + v1Again.execute("again"));
        System.out.println();
        System.out.println("结论：不同时使用的两个同名模块 → 独立加载器 + 用完卸载");
        System.out.println("（卸载前先调 shutdown() 钩子释放资源），互不冲突、可反复热切换，");
        System.out.println("这正是 Tomcat reload / 插件系统的原理。");
    }
}