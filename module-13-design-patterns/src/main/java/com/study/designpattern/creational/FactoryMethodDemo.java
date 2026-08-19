package com.study.designpattern.creational;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 工厂方法模式（Factory Method）用例（常用 + 不常用）
 *
 * 定义一个创建对象的接口，让子类决定实例化哪个类 —— 把"创建逻辑"延迟到子类。
 * 适用：产品种类经常新增、创建逻辑复杂（带参数/校验/日志）。
 *
 * 三种工厂对比（面试高频）：
 *   简单工厂（静态工厂）: 一个类里 switch 创建，新增产品要改代码（违反开闭）
 *   工厂方法           : 每种产品一个工厂子类，新增产品只需加子类（符合开闭）
 *   抽象工厂           : 一个工厂创建一"族"产品（见 AbstractFactoryDemo）
 */
public class FactoryMethodDemo {

    // ---------- 产品 ----------
    public interface Document {
        void open();

        String type();
    }

    public static final class WordDocument implements Document {
        public void open() {
            System.out.println("    Word 文档打开，支持修订/批注");
        }

        public String type() {
            return "word";
        }
    }

    public static final class PdfDocument implements Document {
        public void open() {
            System.out.println("    PDF 文档打开，只读模式");
        }

        public String type() {
            return "pdf";
        }
    }

    // ---------- 工厂（抽象 + 具体） ----------
    public interface DocumentFactory {
        Document create();
    }

    public static final class WordFactory implements DocumentFactory {
        public Document create() {
            return new WordDocument();
        }
    }

    public static final class PdfFactory implements DocumentFactory {
        public Document create() {
            return new PdfDocument();
        }
    }

    // ---------- 不常用：注册表工厂（函数式，避免每新增产品都写一个工厂类） ----------
    public static final class RegistryFactory {
        private static final Map<String, Supplier<Document>> REGISTRY = new HashMap<>(Map.of(
                "word", WordDocument::new,
                "pdf", PdfDocument::new));

        public static void register(String type, Supplier<Document> supplier) {
            REGISTRY.put(type, supplier);
        }

        public static Document create(String type) {
            Supplier<Document> supplier = REGISTRY.get(type);
            if (supplier == null) {
                throw new IllegalArgumentException("未知文档类型: " + type);
            }
            return supplier.get();
        }
    }

    // ---------- 不常用：反射工厂（按类名创建，适合插件式扩展） ----------
    public static final class ReflectionFactory {
        @SuppressWarnings("unchecked")
        public static <T> T create(String className) throws Exception {
            return (T) Class.forName(className).getDeclaredConstructor().newInstance();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 工厂方法：常用写法（每种产品一个工厂） ==========");
        DocumentFactory wordFactory = new WordFactory();
        DocumentFactory pdfFactory = new PdfFactory();
        Document word = wordFactory.create();
        Document pdf = pdfFactory.create();
        System.out.println("  WordFactory.create() -> " + word.type());
        word.open();
        System.out.println("  PdfFactory.create() -> " + pdf.type());
        pdf.open();

        System.out.println();
        System.out.println("========== 工厂方法：不常用写法 ==========");
        System.out.println("  注册表工厂（Supplier 函数式注册，新增类型不用新建类）:");
        System.out.println("    create(\"word\") -> " + RegistryFactory.create("word").type()
                + "，create(\"pdf\") -> " + RegistryFactory.create("pdf").type());
        RegistryFactory.register("excel", () -> new Document() {
            public void open() {
                System.out.println("    Excel 文档打开，支持公式");
            }

            public String type() {
                return "excel";
            }
        });
        System.out.println("    动态注册 excel -> " + RegistryFactory.create("excel").type());

        System.out.println("  反射工厂（Class.forName 按类名创建）:");
        Document reflectDoc = ReflectionFactory.create(
                "com.study.designpattern.creational.FactoryMethodDemo$WordDocument");
        System.out.println("    " + reflectDoc.type() + " 创建成功");
    }
}
