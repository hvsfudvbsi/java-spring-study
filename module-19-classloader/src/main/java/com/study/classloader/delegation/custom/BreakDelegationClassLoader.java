package com.study.classloader.delegation.custom;

import java.nio.file.Path;

/**
 * 打破双亲委派的自定义类加载器（父最后 / parent-last）。
 *
 * 与 {@link FileClassLoader} 的区别：重写了 loadClass，对"自己负责的命名空间"
 * （ownPrefix 前缀）先自己加载，只有自己目录里找不到才回退给父加载器。
 *
 * 为什么要打破双亲委派（现实场景）：
 *  1. SPI（Service Provider Interface）：JDBC、JNDI 等接口定义在 Bootstrap/平台模块，
 *     实现是第三方 jar（在应用类路径或独立目录）——父加载器根本不知道实现类，
 *     必须"父最后"或借助线程上下文类加载器（见 spi/TccLDemo）；
 *  2. 容器隔离：Tomcat 每个 Web 应用一个 WebAppClassLoader，优先加载自己 WEB-INF/lib
 *     下的类（同名类各应用互不影响）；OSGi、Java 插件系统同理；
 *  3. 热部署 / 版本共存：同一接口的不同实现版本放在不同目录，各自加载、互不覆盖。
 *
 * 注意：只对 ownPrefix 前缀的类"父最后"，java.* 等核心类必须继续委派给 Bootstrap，
 * 否则会破坏 JVM 安全（加载到自定义的 java.lang.String）。
 *
 * 测试入口：{@code BreakDelegationClassLoaderTest}
 */
public class BreakDelegationClassLoader extends FileClassLoader {

    /** 本加载器"抢着加载"的命名空间前缀（例如 com.study.classloader.delegation.custom）。 */
    private final String ownPrefix;

    public BreakDelegationClassLoader(Path baseDir, String ownPrefix, ClassLoader parent) {
        super(baseDir, parent);
        this.ownPrefix = ownPrefix;
    }

    /**
     * 打破双亲委派：ownPrefix 命中的类先自己加载（父最后），其余照常父优先。
     * 对照 {@code FileClassLoader}（只重写 findClass）就能看出委派顺序的差异。
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.startsWith(ownPrefix)) {
            // 1. 本加载器已加载过？直接返回（每个加载器对同一名字最多加载一次）。
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }
            // 2. 自己先加载（父最后）：在自己的目录里找字节码。
            try {
                return findClass(name);
            } catch (ClassNotFoundException ignored) {
                // 3. 自己目录里也没有 → 回退给父加载器（委派兜底）。
                return super.loadClass(name);
            }
        }
        // 其他命名空间（java.* 等）：保持标准双亲委派，绝不自己抢。
        return super.loadClass(name);
    }
}