package com.study.classloader.delegation;

import java.util.ArrayList;
import java.util.List;

/**
 * 类加载器层次与"双亲委派模型"演示（JDK 9+）。
 *
 * JDK 9 模块化后，类加载器是三层结构（此前是四层：Bootstrap/Extension/App/自定义）：
 *
 *   Bootstrap ClassLoader（启动类加载器）
 *     - 由 JVM 本身（C++ 实现，Java 里取到的是 null）实现；
 *     - 加载 JDK 核心类：java.*、javax.*（如 java.lang.String）；
 *   Platform ClassLoader（平台类加载器，JDK 9 起取代 Extension）
 *     - 加载 JDK 平台模块类，如 java.sql、javax.sql（如 javax.sql.DataSource）、
 *       java.xml、java.security 等；
 *   Application ClassLoader（应用类加载器，也叫 System ClassLoader）
 *     - 加载 classpath / -cp 下的用户类，即 target/classes 下的所有类；
 *   └── 自定义类加载器（用户继承 ClassLoader 写的，父是 Application）
 *
 * 双亲委派模型：loadClass 时【先让父加载器尝试，父加载不到才自己加载】。
 *  1. 检查该类是否已被本加载器加载（findLoadedClass）；
 *  2. 父加载器存在 → 调父.loadClass（递归向上，直到 Bootstrap）；
 *  3. 父加载不到（抛 ClassNotFoundException）→ 自己 findClass。
 *
 * 为什么必须双亲委派：
 *  1. 安全：防止用户自定义 java.lang.String 覆盖 JDK 核心类（所有 String 请求
 *     都被委派到 Bootstrap，你写的同名类根本没机会被加载）；
 *  2. 一致性：同一个类在 JVM 里只有一份 Class 对象，不会出现"两个 String"互相不认；
 *  3. 缓存复用：父已加载的类子加载器直接复用，避免重复解析。
 *
 * 测试入口：{@code DelegationChainDemoTest}
 */
public class DelegationChainDemo {

    /**
     * 返回委派链上的加载器引用，供测试断言：
     * [0] 应用类加载器、[1] 平台类加载器、[2] 启动类加载器（Java 侧为 null）。
     * 注意不能用 List.of：启动类加载器在 Java 侧是 null，List.of 不允许 null 元素。
     */
    public List<ClassLoader> classLoaderChain() {
        ClassLoader app = ClassLoader.getSystemClassLoader();
        ClassLoader platform = app.getParent();
        ClassLoader bootstrap = platform.getParent();
        List<ClassLoader> chain = new ArrayList<>(3);
        chain.add(app);
        chain.add(platform);
        chain.add(bootstrap);
        return chain;
    }

    /** 打印委派链，并标出"哪些 JDK 类由谁加载"。 */
    public void demo() {
        System.out.println("========== 类加载器层次与双亲委派 ==========");
        List<ClassLoader> chain = classLoaderChain();
        ClassLoader app = chain.get(0);
        ClassLoader platform = chain.get(1);
        ClassLoader bootstrap = chain.get(2);

        System.out.println("应用类加载器 Application  = " + app);
        System.out.println("平台类加载器 Platform     = " + platform);
        System.out.println("启动类加载器 Bootstrap    = " + bootstrap + "  (Java 侧为 null，由 JVM 实现)");
        System.out.println();
        System.out.println("委派方向: 自定义 -> Application -> Platform -> Bootstrap(null)");
        System.out.println();
        System.out.println("【各 JDK 类的加载器】");
        System.out.println("  String.class          -> " + describe(String.class.getClassLoader(), "Bootstrap(null)"));
        System.out.println("  DataSource.class      -> " + describe(javax.sql.DataSource.class.getClassLoader(), "Platform"));
        System.out.println("  DelegationChainDemo   -> " + describe(DelegationChainDemo.class.getClassLoader(), "Application"));
    }

    private static String describe(ClassLoader loader, String fallback) {
        return loader == null ? fallback : loader.toString();
    }

    /**
     * 演示"同一个类在 JVM 中只有一份"：
     * 自定义加载器加载 java.lang.String 时，loadClass 会委派给父 → 最终 Bootstrap，
     * 返回的 Class 对象与直接引用 String.class 是同一个对象（== 相等）。
     */
    public Class<?> loadJdkClassViaCustomLoader(ClassLoader customLoader) throws ClassNotFoundException {
        return customLoader.loadClass("java.lang.String");
    }

    /** 展示委派模型的标准 loadClass 流程（注释版伪代码），供学习对照。 */
    public static String delegationPseudoCode() {
        return """
                1. 调用 loadClass(name)
                2. findLoadedClass(name)  // 本加载器已加载过？直接返回（避免重复加载）
                3. parent != null ?
                     yes -> parent.loadClass(name)      // 双亲委派：先让父加载
                     no  -> bootstrap 尝试加载（Bootstrap 无 Java 对象，由 JVM 内部处理）
                4. 父都加载不到（CNFE）-> findClass(name) // 自己动手：读字节码 + defineClass
                """;
    }
}