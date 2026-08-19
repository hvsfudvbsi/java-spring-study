package com.study.designpattern.creational;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 抽象工厂模式（Abstract Factory）用例（常用 + 不常用）
 *
 * 提供一个创建"一整套相关对象"（产品族）的接口，保证产品之间的配套一致性。
 * 适用：主题换肤（浅色/深色）、跨平台 UI（Windows/Mac）、数据库驱动族。
 *
 * 与工厂方法的区别：
 *   工厂方法 -> 一个工厂只生产"一种"产品
 *   抽象工厂 -> 一个工厂生产"一族"产品（多个产品接口）
 */
public class AbstractFactoryDemo {

    // ---------- 产品族 1：按钮 ----------
    public interface Button {
        String render();
    }

    public static final class LightButton implements Button {
        public String render() {
            return "[浅色按钮 ☀]";
        }
    }

    public static final class DarkButton implements Button {
        public String render() {
            return "[深色按钮 🌙]";
        }
    }

    // ---------- 产品族 2：输入框 ----------
    public interface TextField {
        String render();
    }

    public static final class LightTextField implements TextField {
        public String render() {
            return "[浅色输入框 ☀]";
        }
    }

    public static final class DarkTextField implements TextField {
        public String render() {
            return "[深色输入框 🌙]";
        }
    }

    // ---------- 抽象工厂：一个工厂创建一整套 UI ----------
    public interface UiFactory {
        Button createButton();

        TextField createTextField();

        String theme();
    }

    public static final class LightThemeFactory implements UiFactory {
        public Button createButton() {
            return new LightButton();
        }

        public TextField createTextField() {
            return new LightTextField();
        }

        public String theme() {
            return "浅色主题";
        }
    }

    public static final class DarkThemeFactory implements UiFactory {
        public Button createButton() {
            return new DarkButton();
        }

        public TextField createTextField() {
            return new DarkTextField();
        }

        public String theme() {
            return "深色主题";
        }
    }

    // ---------- 不常用：函数式抽象工厂（Supplier 注册表按主题取工厂） ----------
    public static final class ThemeRegistry {
        private static final Map<String, Supplier<UiFactory>> FACTORIES = Map.of(
                "light", LightThemeFactory::new,
                "dark", DarkThemeFactory::new);

        public static UiFactory of(String theme) {
            Supplier<UiFactory> factory = FACTORIES.get(theme);
            if (factory == null) {
                throw new IllegalArgumentException("未知主题: " + theme);
            }
            return factory.get();
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 抽象工厂：常用写法（按主题成套创建） ==========");
        render(new LightThemeFactory());
        render(new DarkThemeFactory());

        System.out.println();
        System.out.println("========== 抽象工厂：不常用写法（函数式注册表） ==========");
        render(ThemeRegistry.of("dark"));
    }

    /** 客户端只依赖抽象工厂 —— 换主题只需换一行 */
    private static void render(UiFactory factory) {
        System.out.println("  " + factory.theme() + ": "
                + factory.createButton().render() + " + " + factory.createTextField().render());
    }
}
