# module-01-java-basics · Java 基础专题

> 纯 Java 模块，不依赖 Spring。学习 Java 21 的核心语言特性，为后续 Spring 学习打基础。

## 📖 本模块知识点

| 包 | 知识点 | 面试高频点 |
|----|--------|-----------|
| `collections` | 集合框架 | ArrayList vs LinkedList、HashMap 原理（数组+链表+红黑树）、HashSet 底层是 HashMap、TreeMap 排序、不可变集合 |
| `stream` | Stream API | 中间操作 vs 终端操作、惰性求值、groupingBy 分组、reduce 归约、flatMap 扁平化 |
| `optional` | Optional | 避免 NPE、orElse vs orElseGet、orElseThrow、map 链式调用、反模式 |
| `lambda` | Lambda 与函数式接口 | 四大内置函数式接口（Predicate/Function/Consumer/Supplier）、方法引用、@FunctionalInterface |
| `generics` | 泛型 | 泛型方法、通配符 `? extends` / `? super`（PECS 原则）、类型擦除 |
| `records` | record（Java 16+） | 自动生成构造器/getter/equals/hashCode/toString、紧凑构造器校验、与模式匹配解构 |
| `exceptions` | 异常处理 | 受检 vs 非受检、多异常捕获、try-with-resources、自定义业务异常 |
| `concurrency` | 并发编程 | 线程池（Executors）、AtomicInteger、ConcurrentHashMap、CompletableFuture 异步编排 |

## 🚀 运行方式

```bash
# 运行全部 Demo（打印所有知识点示例输出）
mvn compile exec:java -pl module-01-java-basics
# 或在 IDEA 中直接运行 DemoRunner 的 main 方法

# 运行测试
mvn test -pl module-01-java-basics
```

## 🔍 代码导读

1. 入口是 [`DemoRunner`](src/main/java/com/study/javabasics/DemoRunner.java)，依次调用每个 Demo 类的 `demo()` 方法。
2. 每个 Demo 类的代码都有详细中文注释，建议**跟着注释动手修改**，观察输出变化。
3. 测试目录中有 JUnit 5 测试示例（`StreamDemoTest`、`OptionalDemoTest`），展示如何编写单元测试。

## ✍️ 动手练习

1. **Stream**：把 `StreamDemo` 中的学生列表按年龄分组（`groupingBy(age)`），打印每组人数。
2. **Optional**：写一个 `findByEmail(String email)` 方法返回 `Optional<User>`，用 `orElseThrow` 抛出业务异常。
3. **Concurrency**：用 `CompletableFuture` 并行调用 3 个任务，用 `allOf` 等待全部完成后再合并结果。
4. **Generics**：实现一个泛型方法 `swap(List<T> list, int i, int j)` 交换两个位置的元素。
5. **Record**：定义一个 `record Order(String orderNo, List<String> items)`，写一个方法计算总价。

## 📌 进阶提示

- 学完本模块，建议阅读《Effective Java》（第 3 版）的相关章节，尤其是第 7 章（Lambda 和 Stream）。
- 并发是面试重点，建议再深入学习：`ReentrantLock`、`synchronized` 区别、`volatile` 内存可见性、线程池参数（核心/最大线程数、队列、拒绝策略）。
