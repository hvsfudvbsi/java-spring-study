package com.study.classloader.spi;

/**
 * SPI 演示用接口（由"框架侧"定义，即本模块主代码，由 AppClassLoader 加载）。
 *
 * 模拟真实世界的 JDBC Driver 接口：接口定义在 JDK（Bootstrap/平台模块），
 * 实现（如 MySQL Driver）由第三方 jar 提供，位于应用类路径或独立目录。
 * 框架代码只认识这个接口，通过线程上下文类加载器发现实现类并实例化。
 */
public interface Greeting {

    /** 第三方实现需要提供的问候语。 */
    String greet();
}