package com.study.classloader.delegation.custom;

/**
 * 委派 vs 打破委派 对照实验的"父版本"目标类。
 *
 * 本类编译在 target/classes，由应用类加载器（AppClassLoader）加载。
 * 演示时会用运行时编译器再生成一个【同名】TargetClass（内容不同，sayHello 返回
 * "child-version"）放到临时目录：
 *
 *  - 遵循双亲委派（FileClassLoader）：加载同名类时先委派给父 → AppClassLoader 已加载了
 *    本文件 → 返回父版本，"parent-version"；
 *  - 打破双亲委派（BreakDelegationClassLoader）：先在自己的目录里找 → 加载临时目录的
 *    同名类 → "child-version"，且 Class 对象与父版本不是同一个（类身份不同）。
 */
public class TargetClass {

    /** 父（AppClassLoader）版本的行为。 */
    public String sayHello() {
        return "parent-version";
    }
}