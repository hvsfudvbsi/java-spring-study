package com.study.designpattern.structural;

import java.util.HashMap;
import java.util.Map;

/**
 * 享元模式（Flyweight）用例（常用 + 不常用）
 *
 * 通过共享来减少大量细粒度对象的创建 —— 缓存复用。
 * 核心：把对象拆成"内部状态"（可共享、不变）与"外部状态"（随场景变化，使用时传入）。
 *
 * 适用：五子棋棋子（黑白两色共享）、文字排版（同字体复用）、连接池/线程池。
 * JDK 例子：Integer.valueOf 缓存 -128~127、String 常量池、Boolean.TRUE/FALSE。
 */
public class FlyweightDemo {

    /** 享元对象：内部状态 = 名称 + 颜色（不可变，可共享） */
    public static final class ChessPiece {
        private final String name;
        private final String color;

        ChessPiece(String name, String color) {
            this.name = name;
            this.color = color;
        }

        /** 外部状态（棋盘坐标）由调用方传入，不存进对象 */
        public String draw(int x, int y) {
            return name + "(" + color + ") 落在 (" + x + "," + y + ")";
        }
    }

    /** 享元工厂：缓存所有已创建的享元，相同 key 返回同一实例 */
    public static final class ChessPieceFactory {
        private static final Map<String, ChessPiece> CACHE = new HashMap<>();

        public static ChessPiece get(String name, String color) {
            return CACHE.computeIfAbsent(name + "#" + color, key -> new ChessPiece(name, color));
        }

        public static int cachedCount() {
            return CACHE.size();
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 享元：常用写法（棋子共享） ==========");
        ChessPiece black = ChessPieceFactory.get("兵", "黑");
        ChessPiece black2 = ChessPieceFactory.get("兵", "黑");
        ChessPiece white = ChessPieceFactory.get("兵", "白");
        System.out.println("  相同内部状态复用同一实例: " + (black == black2));
        System.out.println("  " + black.draw(1, 2));
        System.out.println("  " + white.draw(1, 7));
        System.out.println("  享元工厂缓存对象数 = " + ChessPieceFactory.cachedCount()
                + "（多次落子也只创建 2 个对象）");

        System.out.println();
        System.out.println("========== 享元：不常用写法（JDK 内置享元） ==========");
        Integer a = Integer.valueOf(127);
        Integer b = Integer.valueOf(127);
        Integer c = Integer.valueOf(128);
        Integer d = Integer.valueOf(128);
        System.out.println("  Integer.valueOf(127) 相同实例: " + (a == b) + "（-128~127 缓存）");
        System.out.println("  Integer.valueOf(128) 相同实例: " + (c == d) + "（超出缓存范围，各自 new）");
        System.out.println("  String 字面量复用常量池: " + ("abc" == "abc"));

        System.out.println("  说明: 线程池/数据库连接池也是享元思想——复用对象，避免反复创建销毁");
    }
}
