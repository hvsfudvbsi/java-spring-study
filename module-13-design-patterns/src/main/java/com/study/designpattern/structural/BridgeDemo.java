package com.study.designpattern.structural;

import java.util.function.Function;

/**
 * 桥接模式（Bridge）用例（常用 + 不常用）
 *
 * 把"抽象部分"（形状）与"实现部分"（渲染器）分离，两者可独立变化，用组合代替继承。
 * 适用：多维变化的场景（形状 x 渲染方式、消息类型 x 发送渠道），避免类爆炸。
 *
 * 对比继承：若用继承，圆形-矢量/圆形-光栅/矩形-矢量/矩形-光栅 = 2x2 个类；
 *           桥接只需 2 + 2 个类，新增维度只需各加一个类。
 * JDK 例子：JDBC 的 DriverManager（抽象）与各数据库 Driver（实现）就是桥接。
 */
public class BridgeDemo {

    /** 实现维度：渲染器 */
    public interface Renderer {
        String render(String shapeDesc);
    }

    public static final class VectorRenderer implements Renderer {
        @Override
        public String render(String shapeDesc) {
            return "[矢量引擎] 绘制 " + shapeDesc;
        }
    }

    public static final class RasterRenderer implements Renderer {
        @Override
        public String render(String shapeDesc) {
            return "[光栅引擎] 绘制 " + shapeDesc;
        }
    }

    /** 抽象维度：形状（持有渲染器引用，组合代替继承） */
    public abstract static class Shape {
        protected final Renderer renderer;   // 桥：抽象 -> 实现

        protected Shape(Renderer renderer) {
            this.renderer = renderer;
        }

        public abstract String draw();

        public abstract double area();
    }

    public static final class Circle extends Shape {
        private final double radius;

        public Circle(Renderer renderer, double radius) {
            super(renderer);
            this.radius = radius;
        }

        @Override
        public String draw() {
            return renderer.render("圆形(半径 " + radius + ")");
        }

        @Override
        public double area() {
            return Math.PI * radius * radius;
        }
    }

    public static final class Rectangle extends Shape {
        private final double w;
        private final double h;

        public Rectangle(Renderer renderer, double w, double h) {
            super(renderer);
            this.w = w;
            this.h = h;
        }

        @Override
        public String draw() {
            return renderer.render("矩形(" + w + "x" + h + ")");
        }

        @Override
        public double area() {
            return w * h;
        }
    }

    /** 不常用：函数式桥（渲染逻辑直接注入，连实现类都不用写） */
    public static final class TextShape {
        private final Function<String, String> renderer;
        private final String desc;

        public TextShape(Function<String, String> renderer, String desc) {
            this.renderer = renderer;
            this.desc = desc;
        }

        public String draw() {
            return renderer.apply(desc);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 桥接：常用写法（形状 x 渲染器自由组合） ==========");
        Renderer vector = new VectorRenderer();
        Renderer raster = new RasterRenderer();

        Shape circle = new Circle(vector, 5);
        Shape rect = new Rectangle(raster, 4, 6);
        Shape circleRaster = new Circle(raster, 3);   // 同一种形状换渲染器
        System.out.println("  " + circle.draw());
        System.out.println("  " + rect.draw());
        System.out.println("  " + circleRaster.draw());

        System.out.println();
        System.out.println("========== 桥接：不常用写法（函数式渲染器） ==========");
        TextShape ascii = new TextShape(desc -> "[ASCII] " + desc, "圆形");
        TextShape json = new TextShape(desc -> "{\"shape\":\"" + desc + "\"}", "圆形");
        System.out.println("  " + ascii.draw());
        System.out.println("  " + json.draw());
        System.out.println("  说明: JDBC DriverManager/Driver、AWT 的 List 与 ListUI 都是桥接");
    }
}
