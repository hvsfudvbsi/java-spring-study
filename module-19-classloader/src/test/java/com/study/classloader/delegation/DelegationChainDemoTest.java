package com.study.classloader.delegation;

import com.study.classloader.delegation.custom.FileClassLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双亲委派链：验证 JDK 9+ 三层类加载器结构，以及"委派给父"的行为。
 */
class DelegationChainDemoTest {

    private final DelegationChainDemo demo = new DelegationChainDemo();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("委派链结构：Application -> Platform -> Bootstrap(null)")
    void chainStructure() {
        List<ClassLoader> chain = demo.classLoaderChain();
        ClassLoader app = chain.get(0);
        ClassLoader platform = chain.get(1);
        ClassLoader bootstrap = chain.get(2);

        assertNotNull(app, "应用类加载器不能为 null");
        assertNotNull(platform, "平台类加载器不能为 null");
        assertSame(platform, ClassLoader.getPlatformClassLoader(), "App 的父应是平台类加载器");
        assertNull(bootstrap, "平台类加载器的父是启动类加载器，Java 侧为 null");
    }

    @Test
    @DisplayName("java.lang.String 由启动类加载器加载（getClassLoader() 为 null）")
    void stringLoadedByBootstrap() {
        assertNull(String.class.getClassLoader(), "核心类由 Bootstrap 加载，Java 侧显示 null");
    }

    @Test
    @DisplayName("javax.sql.DataSource 由平台类加载器加载")
    void dataSourceLoadedByPlatform() {
        assertSame(ClassLoader.getPlatformClassLoader(), javax.sql.DataSource.class.getClassLoader());
    }

    @Test
    @DisplayName("用户类由应用类加载器加载")
    void userClassLoadedByApplication() {
        assertSame(ClassLoader.getSystemClassLoader(), DelegationChainDemo.class.getClassLoader());
    }

    @Test
    @DisplayName("自定义加载器加载 java.lang.String 会委派到 Bootstrap，返回同一份 Class")
    void customLoaderDelegatesJdkClassToBootstrap() throws Exception {
        FileClassLoader custom = new FileClassLoader(tempDir, ClassLoader.getSystemClassLoader());
        Class<?> loaded = demo.loadJdkClassViaCustomLoader(custom);
        // 双亲委派：String 请求一路委派到 Bootstrap，得到的就是 JVM 里那一份 String。
        assertSame(String.class, loaded);
    }

    @Test
    @DisplayName("委派伪代码描述了标准的父优先加载流程")
    void pseudoCodeDescribesParentFirstFlow() {
        String code = DelegationChainDemo.delegationPseudoCode();
        assertTrue(code.contains("findLoadedClass"), "第一步是检查是否已加载");
        assertTrue(code.contains("parent.loadClass"), "第二步是委派给父加载器");
        assertTrue(code.contains("findClass"), "最后才是自己 findClass");
    }
}