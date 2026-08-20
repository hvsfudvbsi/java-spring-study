package com.study.designpattern.structural;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 装饰器模式（Decorator）用例（常用 + 不常用）
 *
 * 不修改原类，通过"包装"动态给对象叠加功能 —— 开闭原则的典范。
 * 适用：加糖加奶的咖啡、加密/压缩/限流的流、日志/缓存的 AOP 增强。
 *
 * 与继承对比：继承是编译期静态增强（每个组合都要一个子类）；
 *             装饰器是运行期动态叠加（任意排列组合，无需新增子类）。
 * JDK 例子：BufferedInputStream 包装 FileInputStream、Collections.synchronizedList 包装 List。
 */
public class DecoratorDemo {

    // ---------- 组件接口 ----------
    public interface Coffee {
        String description();

        double cost();
    }

    /** 基础组件：浓缩咖啡 */
    public static final class Espresso implements Coffee {
        @Override
        public String description() {
            return "浓缩咖啡";
        }

        @Override
        public double cost() {
            return 15;
        }
    }

    /** 基础组件：美式 */
    public static final class Americano implements Coffee {
        @Override
        public String description() {
            return "美式咖啡";
        }

        @Override
        public double cost() {
            return 12;
        }
    }

    /** 装饰器基类：持有被装饰对象，接口不变 */
    public abstract static class CoffeeDecorator implements Coffee {
        protected final Coffee coffee;

        protected CoffeeDecorator(Coffee coffee) {
            this.coffee = coffee;
        }
    }

    /** 具体装饰器：加奶 */
    public static final class MilkDecorator extends CoffeeDecorator {
        public MilkDecorator(Coffee coffee) {
            super(coffee);
        }

        @Override
        public String description() {
            return coffee.description() + "+牛奶";
        }

        @Override
        public double cost() {
            return coffee.cost() + 3;
        }
    }

    /** 具体装饰器：加糖 */
    public static final class SugarDecorator extends CoffeeDecorator {
        public SugarDecorator(Coffee coffee) {
            super(coffee);
        }

        @Override
        public String description() {
            return coffee.description() + "+糖";
        }

        @Override
        public double cost() {
            return coffee.cost() + 2;
        }
    }

    /** 具体装饰器：加奶油 */
    public static final class WhippedCreamDecorator extends CoffeeDecorator {
        public WhippedCreamDecorator(Coffee coffee) {
            super(coffee);
        }

        @Override
        public String description() {
            return coffee.description() + "+奶油";
        }

        @Override
        public double cost() {
            return coffee.cost() + 5;
        }
    }

    /** 不常用：函数式装饰（Function 组合，零类实现） */
    public static Function<String, String> timed(Function<String, String> fn) {
        return input -> {
            long start = System.nanoTime();
            String result = fn.apply(input);
            System.out.println("    [计时装饰] 耗时 " + (System.nanoTime() - start) / 1_000_000 + "ms");
            return result;
        };
    }

    public static void main(String[] args) throws IOException {
        System.out.println("========== 装饰器：常用写法（咖啡层层叠加） ==========");
        Coffee coffee = new Espresso();
        System.out.println("  " + coffee.description() + " = ¥" + coffee.cost());

        coffee = new MilkDecorator(coffee);          // 加奶
        coffee = new SugarDecorator(coffee);         // 加糖
        coffee = new WhippedCreamDecorator(coffee);  // 加奶油
        System.out.println("  叠加后: " + coffee.description() + " = ¥" + coffee.cost());

        // 换个组合，互不影响
        Coffee light = new SugarDecorator(new Americano());
        System.out.println("  另一组合: " + light.description() + " = ¥" + light.cost());

        System.out.println();
        System.out.println("========== 装饰器：不常用写法 ==========");
        // JDK 内置装饰器
        InputStream raw = new ByteArrayInputStream("hello".getBytes());
        InputStream buffered = new BufferedInputStream(raw);   // 字节缓冲装饰
        System.out.println("  BufferedInputStream 装饰 InputStream: 读 " + buffered.read() + " (ASCII 'h')");

        List<String> list = new ArrayList<>(List.of("a"));
        List<String> sync = Collections.synchronizedList(list);   // 同步装饰
        System.out.println("  Collections.synchronizedList 装饰 List: " + sync);

        // 函数式装饰（Function 组合）
        Function<String, String> decorated = timed(String::toUpperCase);
        System.out.println("  函数式装饰: " + decorated.apply("hello"));
    }
}
