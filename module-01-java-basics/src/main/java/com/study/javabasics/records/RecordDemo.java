package com.study.javabasics.records;

/**
 * Record（Java 16 正式引入，Java 21 已非常成熟）
 *
 * record 是"不可变数据载体"的终极简化：自动生成
 *   - 构造器 + 所有字段的 getter（访问器方法名 = 字段名，不带 get 前缀）
 *   - equals / hashCode / toString
 *
 * 适用场景：DTO、VO、返回值封装 —— 纯数据、无行为、不可变。
 *
 * 相比 Lombok @Data 的优势：语言级支持、天然不可变、模式匹配友好。
 */
public class RecordDemo {

    /** 一个 record：一行定义即可拥有完整的数据类能力 */
    public record User(Long id, String name, String email) {}

    /** record 也可以有紧凑构造器做参数校验 */
    public record Point(int x, int y) {
        public Point {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("坐标不能为负数: (" + x + ", " + y + ")");
            }
        }

        /** record 中可以定义额外方法 */
        public double distanceFromOrigin() {
            return Math.sqrt(x * x + y * y);
        }
    }

    public static void demo() {
        System.out.println("【1. record 自动生成构造器 / 访问器 / toString】");
        User user = new User(1L, "张三", "zhangsan@example.com");
        System.out.println("   " + user);
        System.out.println("   访问器 user.name() = " + user.name() + "（注意不是 getName()）");

        System.out.println();
        System.out.println("【2. 自动 equals/hashCode（按值比较）】");
        User user2 = new User(1L, "张三", "zhangsan@example.com");
        System.out.println("   user.equals(user2) = " + user.equals(user2));

        System.out.println();
        System.out.println("【3. 紧凑构造器做参数校验】");
        try {
            new Point(-1, 5);
        } catch (IllegalArgumentException e) {
            System.out.println("   校验生效: " + e.getMessage());
        }
        Point p = new Point(3, 4);
        System.out.println("   Point(3,4) 到原点距离 = " + p.distanceFromOrigin());

        System.out.println();
        System.out.println("【4. record 与模式匹配（Java 21 特性）】");
        Object obj = new User(2L, "李四", "lisi@example.com");
        // 类型模式匹配 + record 解构：一行完成"判断类型 + 取出字段"
        if (obj instanceof User(Long id, String name, String email)) {
            System.out.println("   解构成功: id=" + id + ", name=" + name + ", email=" + email);
        }
    }
}
