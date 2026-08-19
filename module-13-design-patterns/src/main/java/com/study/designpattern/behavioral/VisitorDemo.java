package com.study.designpattern.behavioral;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 访问者模式（Visitor）用例（常用 + 不常用）
 *
 * 在不修改元素类的前提下，为"一组对象"增加新操作 —— 操作与数据结构分离。
 * 核心是"双分派"：元素 accept(visitor) -> visitor.visit(具体元素类型)，由元素决定调用哪个 visit 重载。
 * 适用：报表统计（面积/周长）、编译器 AST 遍历、代码检查工具。
 *
 * 代价：新增一种元素类型要改所有访问者（开闭原则的另一种权衡），
 *       元素少而操作多的场景才划算。
 */
public class VisitorDemo {

    // ---------- 元素接口 ----------
    public interface Shape {
        void accept(ShapeVisitor visitor);
    }

    public static final class Circle implements Shape {
        public final double radius;

        public Circle(double radius) {
            this.radius = radius;
        }

        public void accept(ShapeVisitor visitor) {
            visitor.visit(this);   // 双分派关键：由元素决定调用哪个重载
        }
    }

    public static final class Rectangle implements Shape {
        public final double w;
        public final double h;

        public Rectangle(double w, double h) {
            this.w = w;
            this.h = h;
        }

        public void accept(ShapeVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class Triangle implements Shape {
        public final double base;
        public final double height;

        public Triangle(double base, double height) {
            this.base = base;
            this.height = height;
        }

        public void accept(ShapeVisitor visitor) {
            visitor.visit(this);
        }
    }

    // ---------- 访问者接口（每个元素类型一个 visit 重载） ----------
    public interface ShapeVisitor {
        void visit(Circle circle);

        void visit(Rectangle rectangle);

        void visit(Triangle triangle);
    }

    /** 具体访问者：计算总面积 */
    public static final class AreaVisitor implements ShapeVisitor {
        private double total;

        public void visit(Circle c) {
            total += Math.PI * c.radius * c.radius;
        }

        public void visit(Rectangle r) {
            total += r.w * r.h;
        }

        public void visit(Triangle t) {
            total += t.base * t.height / 2;
        }

        public double total() {
            return total;
        }
    }

    /** 具体访问者：生成描述 */
    public static final class InfoVisitor implements ShapeVisitor {
        private final StringBuilder sb = new StringBuilder();

        public void visit(Circle c) {
            sb.append("圆形(r=").append(c.radius).append(") ");
        }

        public void visit(Rectangle r) {
            sb.append("矩形(").append(r.w).append("x").append(r.h).append(") ");
        }

        public void visit(Triangle t) {
            sb.append("三角形(底=").append(t.base).append(") ");
        }

        public String result() {
            return sb.toString().trim();
        }
    }

    /** 不常用：函数式访问者（用 Map&lt;Class, Function&gt; 代替重载） */
    public static final class FunctionalVisitor {
        private final Map<Class<?>, Function<Shape, String>> handlers;

        public FunctionalVisitor(Map<Class<?>, Function<Shape, String>> handlers) {
            this.handlers = handlers;
        }

        public String visit(Shape shape) {
            Function<Shape, String> fn = handlers.get(shape.getClass());
            return fn == null ? "未知类型" : fn.apply(shape);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 访问者：常用写法（双分派） ==========");
        List<Shape> shapes = List.of(new Circle(2), new Rectangle(3, 4), new Triangle(4, 5));

        AreaVisitor areaVisitor = new AreaVisitor();
        InfoVisitor infoVisitor = new InfoVisitor();
        for (Shape shape : shapes) {
            shape.accept(areaVisitor);     // 每个元素"接待"访问者
            shape.accept(infoVisitor);
        }
        System.out.println("  形状: " + infoVisitor.result());
        System.out.println("  总面积: " + String.format("%.2f", areaVisitor.total()));

        System.out.println();
        System.out.println("========== 访问者：不常用写法（函数式 Map 分派） ==========");
        FunctionalVisitor functionalVisitor = new FunctionalVisitor(Map.of(
                Circle.class, s -> "圆形面积 " + String.format("%.2f", Math.PI * ((Circle) s).radius * ((Circle) s).radius),
                Rectangle.class, s -> "矩形面积 " + (((Rectangle) s).w * ((Rectangle) s).h)));
        System.out.println("  " + functionalVisitor.visit(shapes.get(0)));
        System.out.println("  " + functionalVisitor.visit(shapes.get(1)));
        System.out.println("  说明: 编译器 AST、报表工具常用访问者；元素类型稳定时用 instanceof（Java 21 模式匹配）也可");
    }
}
