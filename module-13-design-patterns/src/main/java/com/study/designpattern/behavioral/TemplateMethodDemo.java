package com.study.designpattern.behavioral;

import java.util.function.Supplier;

/**
 * 模板方法模式（Template Method）用例（常用 + 不常用）
 *
 * 在父类中定义算法骨架，把可变步骤延迟到子类实现 —— "流程固定，细节可变"。
 * 适用：煮咖啡/泡茶、银行开户流程、测试框架（JUnit 的 setUp/tearDown）、Spring JdbcTemplate。
 *
 * 钩子方法（Hook）：父类提供默认实现，子类可覆写以影响流程走向（如 wantCondiments）。
 */
public class TemplateMethodDemo {

    /** 抽象模板：饮品制作（骨架固定） */
    public abstract static class Beverage {
        /** final：不允许子类修改流程顺序 */
        public final String make() {
            StringBuilder log = new StringBuilder();
            log.append(boilWater()).append("\n");
            log.append(brew()).append("\n");
            log.append(pourInCup()).append("\n");
            if (wantCondiments()) {          // 钩子方法：默认加料
                log.append(addCondiments());
            } else {
                log.append("不加任何配料");
            }
            return log.toString();
        }

        private String boilWater() {
            return "烧水到 100°C";
        }

        private String pourInCup() {
            return "倒入杯中";
        }

        protected abstract String brew();          // 抽象步骤：冲泡

        protected abstract String addCondiments(); // 抽象步骤：加料

        /** 钩子：是否加料（子类可覆写） */
        protected boolean wantCondiments() {
            return true;
        }
    }

    /** 具体子类：咖啡 */
    public static final class Coffee extends Beverage {
        protected String brew() {
            return "用滤纸冲泡咖啡粉";
        }

        protected String addCondiments() {
            return "加糖和牛奶";
        }
    }

    /** 具体子类：茶（PlainTea 还要继承它，故不能是 final） */
    public static class Tea extends Beverage {
        protected String brew() {
            return "用热水浸泡茶叶";
        }

        protected String addCondiments() {
            return "加柠檬";
        }
    }

    /** 具体子类：纯茶（覆写钩子，不加料） */
    public static final class PlainTea extends Tea {
        @Override
        protected boolean wantCondiments() {
            return false;   // 钩子控制流程：跳过加料步骤
        }
    }

    /** 不常用：函数式模板（把可变步骤作为参数传入，不需要子类） */
    public static final class LambdaBeverage {
        private final Supplier<String> brew;
        private final Supplier<String> condiments;
        private final boolean wantCondiments;

        public LambdaBeverage(Supplier<String> brew, Supplier<String> condiments, boolean wantCondiments) {
            this.brew = brew;
            this.condiments = condiments;
            this.wantCondiments = wantCondiments;
        }

        public String make() {
            String result = "烧水到 100°C\n" + brew.get() + "\n倒入杯中\n";
            return result + (wantCondiments ? condiments.get() : "不加任何配料");
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 模板方法：常用写法（固定流程 + 可变步骤） ==========");
        System.out.println("  【咖啡】\n  " + new Coffee().make().replace("\n", "\n  "));
        System.out.println("  【茶】\n  " + new Tea().make().replace("\n", "\n  "));
        System.out.println("  【纯茶(钩子=false)】\n  " + new PlainTea().make().replace("\n", "\n  "));

        System.out.println();
        System.out.println("========== 模板方法：不常用写法（函数式模板） ==========");
        LambdaBeverage latte = new LambdaBeverage(
                () -> "蒸汽萃取浓缩咖啡",
                () -> "加奶泡和糖浆",
                true);
        System.out.println("  " + latte.make().replace("\n", "\n  "));
        System.out.println("  说明: JDK 中 Arrays.sort、InputStream.read、Spring 的 JdbcTemplate 都是模板方法");
    }
}
