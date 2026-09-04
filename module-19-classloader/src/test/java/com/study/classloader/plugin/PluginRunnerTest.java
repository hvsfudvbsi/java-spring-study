package com.study.classloader.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件系统测试：两个同名插件版本（模拟两个"服务模块"）不同时使用，
 * 用"独立加载器加载 + 用完卸载"的方式安全切换——直接回答
 * "两个模块不是同时使用，能否使用类的加载/卸载机制"。
 */
class PluginRunnerTest {

    private final PluginRunner runner = new PluginRunner();

    @TempDir
    Path tempDir;

    private Path[] dirs; // [module-v1 目录, module-v2 目录]

    @BeforeEach
    void setUp() throws Exception {
        dirs = runner.preparePlugins(tempDir);
    }

    @Test
    @DisplayName("部署 v1：同名实现类被独立加载器加载，通过共同接口正常执行")
    void loadAndUseV1() throws Exception {
        PluginRunner.PluginHandle v1 = runner.load(dirs[0]);
        assertEquals("plugin-v1", v1.name());
        assertEquals("[v1] processed: hello", v1.execute("hello"));
    }

    @Test
    @DisplayName("部署 v2：与 v1 同名不同实现，行为不同（v2 大写转换）")
    void loadAndUseV2() throws Exception {
        PluginRunner.PluginHandle v2 = runner.load(dirs[1]);
        assertEquals("plugin-v2", v2.name());
        assertEquals("[v2] processed: HELLO", v2.execute("hello"));
    }

    @Test
    @DisplayName("v1 用完卸载：弱引用被清空 = 模块的类已被 GC 卸载")
    void v1UnloadedAfterUse() throws Exception {
        PluginRunner.PluginHandle v1 = runner.load(dirs[0]);
        WeakReference<Class<?>> ref = v1.weakRef();
        assertNotNull(ref.get());

        assertTrue(runner.unloadAndWaitGc(v1, 100),
                "清空加载器/Class 引用后，循环 GC 应能卸载 v1 的类");
    }

    @Test
    @DisplayName("v1 卸载后再部署 v2：得到全新加载器和全新 Class，互不残留")
    void sequentialSwitchV1ToV2() throws Exception {
        // 1. 部署并执行 v1。注意：只能用弱引用记录 v1 的加载器，
        //    强引用持有会让加载器无法被回收、类无法卸载。
        PluginRunner.PluginHandle v1 = runner.load(dirs[0]);
        WeakReference<ClassLoader> loaderV1Ref = new WeakReference<>(v1.weakRef().get().getClassLoader());
        assertEquals("[v1] processed: hello", v1.execute("hello"));

        // 2. 卸载 v1（清空 handle 内强引用 + 循环 GC）。
        assertTrue(runner.unloadAndWaitGc(v1, 100), "v1 的类应被 GC 卸载");

        // 3. 部署 v2：全新加载器（旧加载器要么已被回收，要么是不同实例）。
        PluginRunner.PluginHandle v2 = runner.load(dirs[1]);
        assertTrue(v2.weakRef().get().getClassLoader() != loaderV1Ref.get(),
                "切换后应使用全新加载器");
        assertEquals("[v2] processed: HELLO", v2.execute("hello"));
    }

    @Test
    @DisplayName("反复热切换：v1 → v2 → v1 都能正确加载执行")
    void hotReloadRoundTrip() throws Exception {
        PluginRunner.PluginHandle v1 = runner.load(dirs[0]);
        assertEquals("[v1] processed: a", v1.execute("a"));
        assertTrue(runner.unloadAndWaitGc(v1, 100));

        PluginRunner.PluginHandle v2 = runner.load(dirs[1]);
        assertEquals("[v2] processed: B", v2.execute("b"));
        assertTrue(runner.unloadAndWaitGc(v2, 100));

        PluginRunner.PluginHandle v1Again = runner.load(dirs[0]);
        assertEquals("[v1] processed: c", v1Again.execute("c"));
    }

    @Test
    @DisplayName("卸载前调用 shutdown 钩子：先释放资源再清引用")
    void unloadInvokesShutdownHook() throws Exception {
        PluginRunner.PluginHandle v1 = runner.load(dirs[0]);
        PluginVersion plugin = v1.plugin();
        // 这里故意持有插件实例引用以便检查 shutdown 效果；
        // 注意：业务代码持有实例会阻止类卸载（见 PluginRunner.plugin() 的注释）。
        assertEquals("[v1] processed: hi", v1.execute("hi"));
        assertFalse(isShutDown(plugin), "卸载前资源应处于打开状态");

        runner.unload(v1);

        assertTrue(isShutDown(plugin), "unload 必须先调用 shutdown() 释放资源");
        assertNull(v1.plugin(), "句柄内的强引用应被清空");
    }

    @Test
    @DisplayName("同时部署 v1 和 v2：各自独立加载器，同名类共存且都能用")
    void simultaneousDeployBothVersions() throws Exception {
        PluginRunner.PluginHandle v1 = runner.load(dirs[0]);
        PluginRunner.PluginHandle v2 = runner.load(dirs[1]);
        assertEquals("[v1] processed: x", v1.execute("x"));
        assertEquals("[v2] processed: X", v2.execute("x"));
        assertNotSame(v1.weakRef().get(), v2.weakRef().get(),
                "同时部署时两个版本是不同的 Class");
    }

    /** 反射调用实现类的 isShutDown()（不在接口契约里，验证卸载钩子专用）。 */
    private static boolean isShutDown(PluginVersion plugin) throws Exception {
        return (boolean) plugin.getClass().getMethod("isShutDown").invoke(plugin);
    }
}