package com.study.classloader.conflict;

/**
 * "两个 jar 有相同类名"场景的【兼容层接口】。
 *
 * 面向接口编程是解决 jar 冲突最优雅的手段：
 *  - 接口由框架侧（本模块主代码，AppClassLoader）定义并加载；
 *  - 两个"冲突 jar"里的同名 Greeter 类各自实现本接口；
 *  - 业务代码只依赖接口（AppClassLoader 加载），两个版本各由自己的类加载器加载，
 *    都能 cast 成 IGreeter 直接调用——不需要反射，也不会 ClassCastException。
 *
 * 这对应真实世界里的做法：例如日志门面 slf4j-api（接口）＋ 多个实现绑定
 * （logback / log4j2），或支付网关统一接口 + 各银行渠道实现。
 */
public interface IGreeter {

    /** 两个版本各自的问候语（用于区分谁被执行）。 */
    String hello();
}