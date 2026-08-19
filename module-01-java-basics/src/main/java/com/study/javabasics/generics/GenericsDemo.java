package com.study.javabasics.generics;

import java.util.ArrayList;
import java.util.List;

/**
 * 泛型（Generics）
 *
 * 作用：编译期类型检查，消除强制类型转换，让代码更安全、更通用。
 *
 * 通配符（面试必问）：
 *   ? extends T  上界通配符：可以"读"（生产者 Producer），不能写
 *   ? super T    下界通配符：可以"写"（消费者 Consumer），读出来是 Object
 *   记忆口诀：PECS —— Producer Extends, Consumer Super
 */
public class GenericsDemo {

    /** 泛型方法：T 是类型参数，在方法返回类型前声明 */
    public static <T> T lastElement(List<T> list) {
        return list.get(list.size() - 1);
    }

    /** 上界通配符：只读不写，可以接收 Number 及其子类 */
    public static double sum(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) {
            total += n.doubleValue();
        }
        return total;
    }

    /** 下界通配符：只写不读，可以放入 Integer 及其父类 */
    public static void addIntegers(List<? super Integer> list) {
        list.add(1);
        list.add(2);
    }

    public static void demo() {
        System.out.println("【1. 泛型方法】");
        List<String> words = List.of("a", "b", "c");
        System.out.println("   lastElement(words) = " + lastElement(words));

        System.out.println();
        System.out.println("【2. 上界通配符 ? extends T（PECS 中的 Producer）】");
        System.out.println("   sum(List<Integer>) = " + sum(List.of(1, 2, 3)));
        System.out.println("   sum(List<Double>) = " + sum(List.of(1.5, 2.5)));

        System.out.println();
        System.out.println("【3. 下界通配符 ? super T（PECS 中的 Consumer）】");
        List<Number> numbers = new ArrayList<>();
        addIntegers(numbers); // List<Number> 可以放入 Integer
        System.out.println("   addIntegers(List<Number>) -> " + numbers);

        System.out.println();
        System.out.println("【4. 泛型擦除（面试高频）】");
        System.out.println("   泛型信息在运行时被擦除，List<String> 和 List<Integer> 的 Class 相同: "
                + (new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass()));

        System.out.println();
        System.out.println("【5. 类型安全对比】");
        // 不用泛型：需要手动强转，容易 ClassCastException
        // 用了泛型：编译期就检查类型
        System.out.println("   List<String> 编译期阻止放入 Integer —— 这就是泛型的价值");
    }
}
