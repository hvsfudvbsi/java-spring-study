package com.study.classloader.delegation.custom;

import com.study.classloader.delegation.DelegationBreakDemo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双亲委派 vs 打破双亲委派：同一个全限定名（TargetClass），
 * 遵循委派拿到父版本，打破委派拿到子版本，两者类身份不同。
 */
class BreakDelegationClassLoaderTest {

    private final DelegationBreakDemo demo = new DelegationBreakDemo();

    @TempDir
    Path tempDir;

    private Path childDir;

    @BeforeEach
    void setUp() throws Exception {
        childDir = demo.prepareChildVersion(tempDir);
    }

    @Test
    @DisplayName("遵循双亲委派：同名类委派给父，返回 AppClassLoader 已加载的父版本")
    void delegationReturnsParentVersion() throws Exception {
        Class<?> loaded = demo.loadWithDelegation(childDir);
        assertSame(TargetClass.class, loaded, "父优先：父已加载同名类，应直接复用");
        assertSame(ClassLoader.getSystemClassLoader(), loaded.getClassLoader());
        assertEquals("parent-version", DelegationBreakDemo.invokeSayHello(loaded));
    }

    @Test
    @DisplayName("打破双亲委派：同名类先自己加载，返回目录里的子版本")
    void breakDelegationReturnsChildVersion() throws Exception {
        Class<?> loaded = demo.loadWithBreakDelegation(childDir);
        assertNotSame(TargetClass.class, loaded, "父最后：应加载自己的子版本");
        assertTrue(loaded.getClassLoader() instanceof BreakDelegationClassLoader,
                "子版本应由 BreakDelegationClassLoader 定义");
        assertEquals("child-version", DelegationBreakDemo.invokeSayHello(loaded));
    }

    @Test
    @DisplayName("两种策略加载出的同名类不是同一个 Class（类身份 = 全限定名 + 加载器）")
    void classIdentityDiffers() throws Exception {
        Class<?> delegation = demo.loadWithDelegation(childDir);
        Class<?> breakDelegation = demo.loadWithBreakDelegation(childDir);
        assertNotSame(delegation, breakDelegation);
    }

    @Test
    @DisplayName("打破委派只针对 ownPrefix：java.lang.String 仍委派给 Bootstrap")
    void coreClassesStillDelegated() throws Exception {
        BreakDelegationClassLoader loader = new BreakDelegationClassLoader(
                childDir, "com.study.classloader.delegation.custom", TargetClass.class.getClassLoader());
        assertSame(String.class, loader.loadClass("java.lang.String"),
                "java.* 必须继续走双亲委派，否则会破坏 JVM 安全");
    }

    @Test
    @DisplayName("打破委派兜底：自己目录里没有时回退给父加载器")
    void fallsBackToParentWhenMissingLocally() throws Exception {
        // 用空目录：BreakDelegationClassLoader 在自己目录找不到 TargetClass，
        // 应回退委派给父（AppClassLoader 已加载 TargetClass.class）。
        Path emptyDir = tempDir.resolve("empty");
        java.nio.file.Files.createDirectories(emptyDir);
        BreakDelegationClassLoader loader = new BreakDelegationClassLoader(
                emptyDir, "com.study.classloader.delegation.custom", TargetClass.class.getClassLoader());
        assertSame(TargetClass.class, loader.loadClass(TargetClass.class.getName()));
    }
}