package com.study.classloader.unload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类卸载：验证"类的 Class 对象 + 定义它的加载器都不再被强引用后，GC 会卸载类"。
 *
 * 类可被卸载的三个条件（面试高频）：
 *  1. 类的 Class 对象没有强引用；
 *  2. 定义它的 ClassLoader 没有强引用；
 *  3. 该加载器加载的【所有】类都满足条件 1（卸载是整批的）。
 */
class ClassUnloadDemoTest {

    private final ClassUnloadDemo demo = new ClassUnloadDemo();

    @TempDir
    Path tempDir;

    private Path targetDir;

    @BeforeEach
    void setUp() throws Exception {
        targetDir = demo.prepareTarget(tempDir);
    }

    @Test
    @DisplayName("刚加载的类可正常使用：弱引用非空、静态字段就绪")
    void loadedClassUsable() throws Exception {
        ClassUnloadDemo.LoadResult result = demo.loadAndWatch(targetDir);
        assertNotNull(result.weakRef().get(), "弱引用应指向存活的 Class");
        assertEquals("unloadable-ready",
                result.clazz().getField("MARK").get(null), "静态字段应已初始化");
        assertNotNull(result.clazz().getClassLoader(), "类应由自定义加载器定义");
    }

    @Test
    @DisplayName("清空强引用 + 循环 GC 后，类被卸载（弱引用被清空）")
    void classUnloadedAfterDroppingReferences() throws Exception {
        ClassUnloadDemo.LoadResult result = demo.loadAndWatch(targetDir);
        WeakReference<Class<?>> ref = result.weakRef();

        // 关键：清掉强引用（Class 引用、加载器引用随 result 一起不可达）。
        result = null;

        assertTrue(demo.unloadAndWait(ref, 100),
                "弱引用被清空 = Class 已被回收 = 类已卸载（GC 卸载可能需要多次触发）");
    }

    @Test
    @DisplayName("类卸载后可以重新加载：得到全新的 Class 和全新的加载器")
    void reloadAfterUnloadYieldsFreshClass() throws Exception {
        ClassUnloadDemo.LoadResult first = demo.loadAndWatch(targetDir);
        WeakReference<Class<?>> firstRef = first.weakRef();
        // 注意：只能用弱引用记录旧加载器，不能强引用持有，否则类无法被卸载。
        WeakReference<ClassLoader> firstLoaderRef = new WeakReference<>(first.clazz().getClassLoader());
        first = null;

        assertTrue(demo.unloadAndWait(firstRef, 100), "前置条件：第一次的类应被卸载");

        ClassUnloadDemo.LoadResult second = demo.loadAndWatch(targetDir);
        assertEquals(ClassUnloadDemo.TARGET_NAME, second.clazz().getName(), "同名类可再次加载");
        // 旧 Class 已被回收；新加载的类使用全新加载器（旧加载器要么已被回收，要么是不同实例）。
        assertTrue(second.clazz().getClassLoader() != firstLoaderRef.get(),
                "再次加载应使用全新的加载器");
    }

    @Test
    @DisplayName("加载器仍被强引用时，类不会被卸载")
    void classSurvivesWhileLoaderAlive() throws Exception {
        ClassUnloadDemo.LoadResult result = demo.loadAndWatch(targetDir);
        WeakReference<Class<?>> ref = result.weakRef();

        // 不清引用（result 仍持有 Class 和加载器），类应当存活。
        for (int i = 0; i < 20; i++) {
            System.gc();
            Thread.sleep(10);
        }
        assertNotNull(ref.get(), "加载器/Class 仍被强引用时，类不应被卸载");
    }
}