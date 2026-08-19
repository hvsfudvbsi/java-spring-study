package com.study.javabasics.stream;

import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stream API（Java 8+ 最重要的特性之一）
 *
 * 流水线三步骤：创建流 -> 中间操作（惰性）-> 终端操作（触发执行）
 *
 * 常用中间操作：filter / map / flatMap / distinct / sorted / limit / skip / peek
 * 常用终端操作：forEach / collect / count / reduce / min / max / anyMatch / allMatch
 *
 * 注意：Stream 只能消费一次！再次使用会抛 IllegalStateException。
 */
public class StreamDemo {

    private record Student(String name, int age, String major) {}

    public static void demo() {
        List<Student> students = List.of(
                new Student("小明", 20, "计算机"),
                new Student("小红", 21, "计算机"),
                new Student("小刚", 22, "数学"),
                new Student("小丽", 19, "数学"),
                new Student("小华", 23, "物理")
        );

        System.out.println("【1. filter + map + collect】筛选计算机专业的学生姓名");
        List<String> csNames = students.stream()
                .filter(s -> "计算机".equals(s.major()))
                .map(Student::name)
                .collect(Collectors.toList());
        System.out.println("   " + csNames);

        System.out.println();
        System.out.println("【2. sorted】按年龄排序");
        students.stream()
                .sorted(Comparator.comparingInt(Student::age))
                .forEach(s -> System.out.print("   " + s.name() + "(" + s.age() + ")"));
        System.out.println();

        System.out.println();
        System.out.println("【3. groupingBy】按专业分组");
        Map<String, List<Student>> byMajor = students.stream()
                .collect(Collectors.groupingBy(Student::major));
        byMajor.forEach((major, list) ->
                System.out.println("   " + major + ": " + list.stream().map(Student::name).toList()));

        System.out.println();
        System.out.println("【4. 统计】年龄总和 / 平均 / 最大");
        IntSummaryStatistics stats = students.stream()
                .mapToInt(Student::age)
                .summaryStatistics();
        System.out.println("   总和=" + stats.getSum() + " 平均=" + stats.getAverage()
                + " 最大=" + stats.getMax() + " 最小=" + stats.getMin());

        System.out.println();
        System.out.println("【5. 匹配与归约】");
        boolean allAdult = students.stream().allMatch(s -> s.age() >= 18);
        boolean anyMath = students.stream().anyMatch(s -> "数学".equals(s.major()));
        System.out.println("   全部成年? " + allAdult + "，有数学专业? " + anyMath);

        int totalAge = students.stream().mapToInt(Student::age).sum();
        int totalAgeReduce = students.stream()
                .map(Student::age)
                .reduce(0, Integer::sum); // reduce：把流元素逐个累积
        System.out.println("   sum=" + totalAge + "，reduce 求和=" + totalAgeReduce);

        System.out.println();
        System.out.println("【6. 其他常用操作】");
        String joined = students.stream().map(Student::name).collect(Collectors.joining(", "));
        System.out.println("   joining: " + joined);

        List<Integer> distinct = List.of(1, 2, 2, 3, 3, 3).stream().distinct().toList();
        System.out.println("   distinct: " + distinct);

        List<Integer> first3 = List.of(1, 2, 3, 4, 5).stream().limit(3).toList();
        System.out.println("   limit(3): " + first3);

        // 扁平化：把多个集合合并成一个流
        List<List<String>> nested = List.of(List.of("a", "b"), List.of("c", "d"));
        List<String> flat = nested.stream().flatMap(List::stream).toList();
        System.out.println("   flatMap: " + flat);
    }
}
