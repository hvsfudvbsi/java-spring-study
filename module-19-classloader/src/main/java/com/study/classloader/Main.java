package com.study.classloader;

import com.study.classloader.basic.ClassLoadingLifecycleDemo;
import com.study.classloader.conflict.ClassConflictDemo;
import com.study.classloader.conflict.IsolationDemo;
import com.study.classloader.delegation.DelegationBreakDemo;
import com.study.classloader.delegation.DelegationChainDemo;
import com.study.classloader.delegation.custom.JarClassLoaderDemo;
import com.study.classloader.plugin.PluginRunner;
import com.study.classloader.spi.TccLDemo;
import com.study.classloader.unload.ClassUnloadDemo;

/**
 * module-19-classloader 演示入口。
 *
 * 运行方式（纯 Java 模块，不依赖 Spring）：
 *   mvn compile exec:java -pl module-19-classloader -Dexec.mainClass=com.study.classloader.Main
 *   或在 IDEA 中直接运行本类 main 方法。
 *
 * 依次演示 9 个知识点：
 *  1. 类加载生命周期与初始化触发时机（basic/ClassLoadingLifecycleDemo）
 *  2. 类加载器层次与双亲委派（delegation/DelegationChainDemo）
 *  3. 双亲委派 vs 打破双亲委派（delegation/DelegationBreakDemo）
 *  4. SPI 与线程上下文类加载器（spi/TccLDemo）
 *  5. 类冲突：同名类先加载生效（conflict/ClassConflictDemo）
 *  6. 隔离加载：两个 jar 同名类共存（conflict/IsolationDemo）
 *  7. 类卸载：WeakReference + GC 验证（unload/ClassUnloadDemo）
 *  8. 插件系统：同名模块不同时使用，加载/卸载热切换（plugin/PluginRunner）
 *  9. jar 文件加载：JarClassLoader 从真实 jar 读字节码（delegation/custom/JarClassLoaderDemo）
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("========== module-19-classloader · 类加载机制 ==========");
        System.out.println();

        // 1. 类加载生命周期：哪些操作触发初始化，哪些不触发。
        new ClassLoadingLifecycleDemo().demo();
        System.out.println();

        // 2. 双亲委派链：Bootstrap(null) <- Platform <- Application。
        new DelegationChainDemo().demo();
        System.out.println();

        // 3. 打破双亲委派：同一个全限定名，两种加载策略得到不同类身份。
        new DelegationBreakDemo().demo();
        System.out.println();

        // 4. SPI / 线程上下文类加载器：框架如何加载"看不见"的第三方实现。
        new TccLDemo().demo();
        System.out.println();

        // 5. 类冲突：同一个类加载器里，同名类先加载生效（classpath 顺序决定）。
        new ClassConflictDemo().demo();
        System.out.println();

        // 6. 隔离加载：两个 jar 同名类同时使用 → 每个 jar 一个专属加载器 + 共同接口。
        new IsolationDemo().demo();
        System.out.println();

        // 7. 类卸载：类生命周期终点，WeakReference 验证。
        new ClassUnloadDemo().demo();
        System.out.println();

        // 8. 插件系统：两个同名服务模块不同时使用 → 加载/卸载热切换。
        new PluginRunner().demo();
        System.out.println();

        // 9. jar 文件加载：JarClassLoader 从真实 jar 读字节码，与目录加载对照。
        new JarClassLoaderDemo().demo();
        System.out.println();

        System.out.println("========== 全部 Demo 执行完毕 ==========");
    }
}