package com.study.classloader.plugin;

/**
 * 插件系统演示用接口（模拟"两个服务模块"共用的统一契约）。
 *
 * 场景：我们有 plugin-v1 和 plugin-v2 两个"服务模块"，它们只是同一个功能的两个版本，
 * 【不会同时使用】——部署方决定当前用哪个。两个模块都打包成 jar，
 * 里面都有一个同名类 GreetingPlugin 实现本接口。
 *
 * 为什么需要接口：接口由宿主程序（AppClassLoader）加载，两个版本的 GreetingPlugin
 * 由各自的类加载器加载，但只要它们实现同一个接口，宿主就能用接口类型直接调用，
 * 完全不需要反射，也避免了跨类加载器的 ClassCastException。
 */
public interface PluginVersion {

    /** 插件版本名（用于区分 v1 / v2）。 */
    String name();

    /** 执行业务逻辑，返回处理结果。 */
    String execute(String input);
}