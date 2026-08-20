package com.study.designpattern.structural;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 代理模式（Proxy）用例（常用 + 不常用）
 *
 * 为对象提供一个替身，控制对原对象的访问 —— 在原对象前后插入逻辑而不改原代码。
 * 适用：日志、权限校验、延迟加载、远程调用、AOP（Spring 动态代理）。
 *
 * 种类：
 *   静态代理     : 手写代理类（一个接口一个代理类）
 *   JDK 动态代理 : 运行时生成，只支持接口（InvocationHandler）
 *   虚拟代理     : 延迟创建"重"对象，真正用到才加载
 *   保护代理     : 控制访问权限
 * （Spring AOP 中 JDK 代理只能代理接口，CGLIB 可代理类，见 module-06-spring-aop）
 */
public class ProxyDemo {

    // ---------- 目标接口与实现 ----------
    public interface UserService {
        String findUser(String id);

        void updateUser(String id, String name);
    }

    public static final class UserServiceImpl implements UserService {
        @Override
        public String findUser(String id) {
            return "用户{id=" + id + ", name=张三}";
        }

        @Override
        public void updateUser(String id, String name) {
            System.out.println("    更新用户 " + id + " 为 " + name);
        }
    }

    /** 静态代理：手写，方法前后加日志 */
    public static final class UserServiceLogProxy implements UserService {
        private final UserService target;

        public UserServiceLogProxy(UserService target) {
            this.target = target;
        }

        @Override
        public String findUser(String id) {
            System.out.println("    [日志] 调用 findUser(" + id + ")");
            String result = target.findUser(id);
            System.out.println("    [日志] 返回 " + result);
            return result;
        }

        @Override
        public void updateUser(String id, String name) {
            System.out.println("    [日志] 调用 updateUser(" + id + ", " + name + ")");
            target.updateUser(id, name);
        }
    }

    /** 不常用：JDK 动态代理（运行时生成代理，一个 InvocationHandler 通用所有方法） */
    public static final class TimerInvocationHandler implements InvocationHandler {
        private final Object target;

        public TimerInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long start = System.nanoTime();
            Object result = method.invoke(target, args);
            System.out.println("    [动态代理] " + method.getName()
                    + " 耗时 " + (System.nanoTime() - start) / 1_000_000 + "ms");
            return result;
        }
    }

    /** 不常用：虚拟代理（延迟加载大对象） */
    public static final class LazyImage {
        private final String path;
        private String loadedContent;   // 真正加载后的内容

        public LazyImage(String path) {
            this.path = path;
        }

        public String display() {
            if (loadedContent == null) {
                loadedContent = "加载图片内容: " + path;   // 首次访问才"加载"
                System.out.println("    [虚拟代理] 首次访问，真正加载 " + path);
            }
            return loadedContent;
        }
    }

    /** 不常用：保护代理（权限校验） */
    public static final class AdminProxy implements UserService {
        private final UserService target;
        private final boolean isAdmin;

        public AdminProxy(UserService target, boolean isAdmin) {
            this.target = target;
            this.isAdmin = isAdmin;
        }

        @Override
        public String findUser(String id) {
            return target.findUser(id);
        }

        @Override
        public void updateUser(String id, String name) {
            if (!isAdmin) {
                throw new SecurityException("无权限修改用户（保护代理拦截）");
            }
            target.updateUser(id, name);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 代理：常用写法（静态代理加日志） ==========");
        UserService real = new UserServiceImpl();
        UserService logProxy = new UserServiceLogProxy(real);
        logProxy.findUser("1001");
        logProxy.updateUser("1001", "李四");

        System.out.println();
        System.out.println("========== 代理：不常用写法 ==========");
        // JDK 动态代理
        UserService dynamic = (UserService) Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                new TimerInvocationHandler(real));
        System.out.println("  动态代理返回: " + dynamic.findUser("2002"));

        // 虚拟代理（延迟加载）
        LazyImage image = new LazyImage("/img/hero.png");
        System.out.println("  创建虚拟代理（未加载）...");
        System.out.println("  第一次 display: " + image.display());
        System.out.println("  第二次 display: " + image.display() + "（直接复用，不再加载）");

        // 保护代理
        UserService admin = new AdminProxy(real, true);
        UserService guest = new AdminProxy(real, false);
        admin.updateUser("1", "管理员改名");
        try {
            guest.updateUser("1", "游客改名");
        } catch (SecurityException e) {
            System.out.println("  " + e.getMessage());
        }
    }
}
