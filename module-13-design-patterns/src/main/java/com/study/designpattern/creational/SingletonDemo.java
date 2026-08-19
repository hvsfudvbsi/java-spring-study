package com.study.designpattern.creational;

import java.lang.reflect.Constructor;

/**
 * 单例模式（Singleton）用例（常用 + 不常用）
 *
 * 保证一个类只有一个实例，并提供全局访问点。
 * 适用：配置中心、连接池、线程池、日志器、Spring 容器中的 Bean（默认单例）。
 *
 * 面试必问：
 *   1. 懒汉式 DCL 为什么需要 volatile？—— 防止指令重排导致其他线程拿到"半初始化"对象
 *   2. 枚举单例为什么最安全？—— JVM 保证枚举实例唯一，天然防反射、防序列化破坏
 *   3. 反射/序列化如何破坏单例？—— 反射可调用私有构造器再建实例；反序列化会新建实例（用 readResolve 兜底）
 */
public class SingletonDemo {

    /** 常用写法一：饿汉式（类加载即创建，天然线程安全，缺点：不用也会创建） */
    public static final class Eager {
        private static final Eager INSTANCE = new Eager();

        private Eager() {
        }

        public static Eager getInstance() {
            return INSTANCE;
        }
    }

    /** 常用写法二：懒汉式 + 双重检查锁（DCL），面试最高频写法 */
    public static final class LazyDcl {
        // volatile 防止指令重排导致读到半初始化对象（new 在字节码层分 3 步）
        private static volatile LazyDcl instance;

        private LazyDcl() {
        }

        public static LazyDcl getInstance() {
            if (instance == null) {            // 第一次检查：避免无谓加锁
                synchronized (LazyDcl.class) { // 加锁
                    if (instance == null) {    // 第二次检查：防止多个线程重复创建
                        instance = new LazyDcl();
                    }
                }
            }
            return instance;
        }
    }

    /** 常用写法三：静态内部类（Initialization-on-demand holder，兼顾懒加载与线程安全） */
    public static final class Holder {
        private Holder() {
        }

        private static final class InstanceHolder {
            static final Holder INSTANCE = new Holder();
        }

        public static Holder getInstance() {
            return InstanceHolder.INSTANCE;
        }
    }

    /** 不常用写法：枚举单例（Joshua Bloch 在《Effective Java》中推荐，最安全） */
    public enum EnumSingleton {
        INSTANCE;

        private int count;

        public int add() {
            return ++count;
        }
    }

    /** 不常用：防反射/防序列化破坏的加固写法 */
    public static final class AntiReflection {
        private static final AntiReflection INSTANCE = new AntiReflection();

        private AntiReflection() {
            // 第二次调用构造器（反射攻击）直接拒绝
            if (INSTANCE != null) {
                throw new IllegalStateException("单例禁止反射创建第二个实例");
            }
        }

        // 反序列化时返回同一实例，防止 new 出一个新实例
        protected Object readResolve() {
            return INSTANCE;
        }

        public static AntiReflection getInstance() {
            return INSTANCE;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 单例模式：常用写法 ==========");
        System.out.println("  饿汉式      : 两次 getInstance 同一实例 = " + (Eager.getInstance() == Eager.getInstance()));
        System.out.println("  懒汉式 DCL  : 两次 getInstance 同一实例 = " + (LazyDcl.getInstance() == LazyDcl.getInstance()));
        System.out.println("  静态内部类  : 两次 getInstance 同一实例 = " + (Holder.getInstance() == Holder.getInstance()));

        System.out.println();
        System.out.println("========== 单例模式：不常用写法 ==========");
        System.out.println("  枚举单例    : " + (EnumSingleton.INSTANCE == EnumSingleton.INSTANCE)
                + "，count=" + EnumSingleton.INSTANCE.add());

        // 反射攻击演示：普通单例可以被反射破坏，枚举单例不行
        System.out.println("  反射攻击演示：");
        Constructor<LazyDcl> c = LazyDcl.class.getDeclaredConstructor();
        c.setAccessible(true);
        LazyDcl second = c.newInstance();
        System.out.println("    ❌ 普通单例被反射创建出第二个实例: " + (second != LazyDcl.getInstance()));

        Constructor<?> enumCtor = EnumSingleton.class.getDeclaredConstructors()[0];
        enumCtor.setAccessible(true);
        try {
            enumCtor.newInstance();
            System.out.println("    ❌ 枚举单例被反射创建");
        } catch (Exception e) {
            System.out.println("    ✅ 枚举单例反射被拦截: " + e.getClass().getSimpleName()
                    + "（枚举构造器带 String/int 参数，JVM 禁止 newInstance）");
        }

        System.out.println("  加固单例    : " + (AntiReflection.getInstance() == AntiReflection.getInstance()));
        try {
            Constructor<AntiReflection> anti = AntiReflection.class.getDeclaredConstructor();
            anti.setAccessible(true);
            anti.newInstance();
        } catch (Exception e) {
            System.out.println("    AntiReflection 被构造器内校验拦截: " + e.getCause().getMessage());
        }
    }
}
