package com.study.designpattern.creational;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原型模式（Prototype）用例（常用 + 不常用）
 *
 * 用"克隆已有对象"代替"new 新对象"，避免重复初始化，适合创建成本高/对象结构复杂的场景。
 * 适用：图形编辑器复制形状、配置模板、游戏批量生成小兵。
 *
 * 面试必问：
 *   1. 浅拷贝 vs 深拷贝：clone() 默认浅拷贝，引用类型字段仍共享同一对象
 *   2. Cloneable 是标记接口，不实现 clone() 会抛 CloneNotSupportedException
 *   3. record 天然不可变，用 with 风格拷贝即可，不需要 Cloneable
 */
public class PrototypeDemo {

    /** 原型基类：实现 Cloneable，提供 clone() */
    public abstract static class Shape implements Cloneable {
        protected String color;
        protected int x;
        protected int y;

        public Shape(String color, int x, int y) {
            this.color = color;
            this.x = x;
            this.y = y;
        }

        public abstract double area();

        public void move(int dx, int dy) {
            this.x += dx;
            this.y += dy;
        }

        @Override
        public Shape clone() {
            try {
                return (Shape) super.clone();   // 浅拷贝：引用字段共享
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "(color=" + color + ", x=" + x + ", y=" + y + ", area=" + area() + ")";
        }
    }

    public static final class Circle extends Shape {
        private final double radius;

        public Circle(String color, int x, int y, double radius) {
            super(color, x, y);
            this.radius = radius;
        }

        public double area() {
            return Math.PI * radius * radius;
        }
    }

    public static final class Rectangle extends Shape {
        private final double w;
        private final double h;

        public Rectangle(String color, int x, int y, double w, double h) {
            super(color, x, y);
            this.w = w;
            this.h = h;
        }

        public double area() {
            return w * h;
        }
    }

    /** 不常用：深拷贝（引用字段也要拷贝，避免克隆体和原型共享可变状态） */
    public static final class Drawing implements Cloneable {
        // 注意：clone() 需要重建集合，所以不能是 final
        private List<Shape> shapes = new ArrayList<>();
        private Map<String, String> meta = new HashMap<>();

        public void add(Shape s) {
            shapes.add(s);
        }

        public void putMeta(String k, String v) {
            meta.put(k, v);
        }

        public List<Shape> shapes() {
            return shapes;
        }

        public Map<String, String> meta() {
            return meta;
        }

        @Override
        public Drawing clone() {
            try {
                Drawing copy = (Drawing) super.clone();
                // 深拷贝：集合要新建（浅拷贝会把同一个集合引用共享给克隆体），元素也逐一克隆
                copy.shapes = new ArrayList<>(shapes.stream().map(Shape::clone).toList());
                copy.meta = new HashMap<>(meta);
                return copy;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** 不常用：原型注册表（用 Map 缓存原型，按 key 取克隆体） */
    public static final class ShapeRegistry {
        private static final Map<String, Shape> PROTOTYPES = new HashMap<>();

        public static void register(String key, Shape prototype) {
            PROTOTYPES.put(key, prototype);
        }

        public static Shape create(String key) {
            Shape prototype = PROTOTYPES.get(key);
            return prototype == null ? null : prototype.clone();
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 原型：常用写法（clone 浅拷贝） ==========");
        Circle redCircle = new Circle("red", 0, 0, 5);
        Circle clone = (Circle) redCircle.clone();   // clone() 返回 Shape，需要向下转型
        clone.move(10, 10);   // 修改克隆体不影响原型
        System.out.println("  原型  : " + redCircle);
        System.out.println("  克隆体: " + clone + "（不是同一个对象: " + (redCircle != clone) + "）");

        System.out.println();
        System.out.println("========== 原型：不常用写法 ==========");
        // 深拷贝：克隆体的 meta 修改不影响原图
        Drawing original = new Drawing();
        original.add(new Circle("blue", 1, 1, 2));
        original.putMeta("author", "buffy");
        Drawing deepCopy = original.clone();
        deepCopy.putMeta("author", "copy");
        System.out.println("  深拷贝后，原图 meta=" + original.meta() + "，克隆体 meta=" + deepCopy.meta()
                + "（互不影响，集合也不共享）");

        // 原型注册表
        ShapeRegistry.register("circle", new Circle("green", 0, 0, 3));
        ShapeRegistry.register("rect", new Rectangle("yellow", 0, 0, 4, 5));
        Shape c1 = ShapeRegistry.create("circle");
        Shape c2 = ShapeRegistry.create("circle");
        System.out.println("  原型注册表: 两次 create(\"circle\") 是不同对象 -> " + (c1 != c2) + "，内容相同 -> " + c1);
    }
}
