package com.study.designpattern;

import com.study.designpattern.creational.AbstractFactoryDemo;
import com.study.designpattern.creational.BuilderDemo;
import com.study.designpattern.creational.FactoryMethodDemo;
import com.study.designpattern.creational.PrototypeDemo;
import com.study.designpattern.creational.SingletonDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 创建型模式验证：单例唯一性、工厂产出正确、抽象工厂产品族一致性、
 * 建造者校验、原型深浅拷贝
 */
class CreationalPatternsTest {

    @Test
    @DisplayName("单例：饿汉式/懒汉式 DCL/静态内部类/枚举 都返回同一实例")
    void singletonUnique() {
        assertSame(SingletonDemo.Eager.getInstance(), SingletonDemo.Eager.getInstance());
        assertSame(SingletonDemo.LazyDcl.getInstance(), SingletonDemo.LazyDcl.getInstance());
        assertSame(SingletonDemo.Holder.getInstance(), SingletonDemo.Holder.getInstance());
        assertSame(SingletonDemo.EnumSingleton.INSTANCE, SingletonDemo.EnumSingleton.INSTANCE);
        assertSame(SingletonDemo.AntiReflection.getInstance(), SingletonDemo.AntiReflection.getInstance());
    }

    @Test
    @DisplayName("单例：加固单例反射创建第二个实例会被拦截")
    void singletonAntiReflection() throws Exception {
        var ctor = SingletonDemo.AntiReflection.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThrows(Exception.class, ctor::newInstance);
    }

    @Test
    @DisplayName("工厂方法：注册表工厂按类型产出正确产品，支持动态注册")
    void factoryMethodRegistry() {
        FactoryMethodDemo.RegistryFactory.register("excel", () -> new FactoryMethodDemo.Document() {
            @Override
            public void open() {
            }

            @Override
            public String type() {
                return "excel";
            }
        });
        assertEquals("word", FactoryMethodDemo.RegistryFactory.create("word").type());
        assertEquals("pdf", FactoryMethodDemo.RegistryFactory.create("pdf").type());
        assertEquals("excel", FactoryMethodDemo.RegistryFactory.create("excel").type());
        assertThrows(IllegalArgumentException.class, () -> FactoryMethodDemo.RegistryFactory.create("unknown"));
    }

    @Test
    @DisplayName("抽象工厂：同一主题工厂产出配套产品族（深浅色一致）")
    void abstractFactoryFamilyConsistency() {
        AbstractFactoryDemo.UiFactory dark = new AbstractFactoryDemo.DarkThemeFactory();
        assertTrue(dark.createButton().render().contains("深色"));
        assertTrue(dark.createTextField().render().contains("深色"));

        AbstractFactoryDemo.UiFactory light = AbstractFactoryDemo.ThemeRegistry.of("light");
        assertTrue(light.createButton().render().contains("浅色"));
        assertEquals("浅色主题", light.theme());
        assertThrows(IllegalArgumentException.class, () -> AbstractFactoryDemo.ThemeRegistry.of("unknown"));
    }

    @Test
    @DisplayName("建造者：链式构建产出正确对象，缺必填项 build 时拦截")
    void builderBuildAndValidate() {
        BuilderDemo.HttpRequest request = BuilderDemo.HttpRequest.builder()
                .url("http://localhost:8080/api")
                .method("POST")
                .header("Content-Type", "application/json")
                .build();
        assertEquals("http://localhost:8080/api", request.url());
        assertEquals("POST", request.method());
        assertEquals(Map.of("Content-Type", "application/json"), request.headers());
        // 缺 url 必须抛异常
        assertThrows(IllegalStateException.class, () -> BuilderDemo.HttpRequest.builder().build());
    }

    @Test
    @DisplayName("原型：clone 是不同对象但内容相同；深拷贝的引用字段互不影响")
    void prototypeCloneAndDeepCopy() {
        PrototypeDemo.Circle circle = new PrototypeDemo.Circle("red", 0, 0, 5);
        PrototypeDemo.Circle clone = (PrototypeDemo.Circle) circle.clone();   // clone() 返回 Shape
        assertNotSame(circle, clone);
        assertEquals(circle.area(), clone.area());

        PrototypeDemo.Drawing original = new PrototypeDemo.Drawing();
        original.putMeta("author", "buffy");
        PrototypeDemo.Drawing copy = original.clone();
        copy.putMeta("author", "copy");
        assertEquals("buffy", original.meta().get("author"));
        assertNotSame(original.meta(), copy.meta());
    }
}
