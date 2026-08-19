package com.study.designpattern.behavioral;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * 迭代器模式（Iterator）用例（常用 + 不常用）
 *
 * 提供统一方式顺序访问集合元素，不暴露内部结构。
 * Java 中 Iterator/Iterable 已内置，for-each 就是语法糖。
 *
 * 面试必问：
 *   1. fail-fast（快速失败）：ArrayList 迭代中结构性修改 -> ConcurrentModificationException
 *   2. 弱一致迭代器：ConcurrentHashMap 迭代不抛异常、不保证看到最新数据
 *   3. 内部迭代 vs 外部迭代：for-each 是外部迭代（客户端控制节奏）；Stream/forEach 是内部迭代
 */
public class IteratorDemo {

    /** 元素类型 */
    public record Book(String title, int pages) {
    }

    /** 自定义集合：书架（实现 Iterable，支持 for-each） */
    public static final class BookShelf implements Iterable<Book> {
        private final List<Book> books = new ArrayList<>();

        public void add(Book book) {
            books.add(book);
        }

        public int size() {
            return books.size();
        }

        @Override
        public Iterator<Book> iterator() {
            return books.iterator();
        }

        /** 不常用：自定义迭代器（演示迭代器接口本身）—— 倒序遍历 */
        public Iterator<Book> reverseIterator() {
            return new Iterator<>() {
                private int index = books.size() - 1;

                public boolean hasNext() {
                    return index >= 0;
                }

                public Book next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return books.get(index--);
                }
            };
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 迭代器：常用写法（for-each） ==========");
        BookShelf shelf = new BookShelf();
        shelf.add(new Book("Java 编程思想", 800));
        shelf.add(new Book("设计模式", 400));
        shelf.add(new Book("深入理解 JVM", 600));

        System.out.println("  正序遍历（for-each 等价于 iterator()）:");
        for (Book book : shelf) {
            System.out.println("    " + book);
        }

        System.out.println();
        System.out.println("========== 迭代器：不常用写法 ==========");
        // 自定义迭代器：倒序
        System.out.println("  倒序遍历（自定义 Iterator）:");
        Iterator<Book> reverse = shelf.reverseIterator();
        while (reverse.hasNext()) {
            System.out.println("    " + reverse.next());
        }

        // 迭代器 -> Stream（Spliterator 桥接）
        long totalPages = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(shelf.iterator(), Spliterator.ORDERED), false)
                .mapToLong(Book::pages)
                .sum();
        System.out.println("  Iterator 转 Stream 求总页数 = " + totalPages);

        // fail-fast 演示：迭代中修改集合
        System.out.println("  fail-fast 演示:");
        try {
            List<String> list = new ArrayList<>(List.of("a", "b", "c"));
            for (String s : list) {
                if (s.equals("a")) {
                    list.add("x");   // 结构性修改
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("    迭代中 add -> ConcurrentModificationException ✅");
        }
    }
}
