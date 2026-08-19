package com.study.designpattern.practice;

import java.util.HashMap;
import java.util.Map;

/**
 * 实操示例三：文档导出中心（组合 4 个设计模式）
 *
 * 场景：把不同来源的数据（内部数据 / 遗留系统数据）统一导出为文档，
 *       导出时可叠加"水印/压缩/加密"等能力，一键完成。
 *
 * 用到的模式：
 *   适配器 : LegacyDataSourceAdapter 把遗留系统接口适配成统一 DataSource
 *   享元   : FontFactory 复用字体对象（同参数只创建一次）
 *   装饰器 : 加密/压缩/水印层层包装 Exporter，可任意组合
 *   外观   : ExportFacade 提供"普通导出 / 安全导出"两个一键入口
 */
public class DocumentExportDemo {

    // ================= 适配器 =================

    /** 统一数据源接口（目标接口） */
    public interface DataSource {
        String content();
    }

    /** 内部数据源 */
    public static final class InternalDataSource implements DataSource {
        private final String content;

        public InternalDataSource(String content) {
            this.content = content;
        }

        public String content() {
            return content;
        }
    }

    /** 第三方/遗留数据源：接口不兼容（无法直接当 DataSource 用） */
    public static final class LegacyDataSource {
        public String fetchRaw() {
            return "遗留系统数据（老接口）";
        }
    }

    /** 对象适配器：把遗留数据源适配成统一接口 */
    public static final class LegacyDataSourceAdapter implements DataSource {
        private final LegacyDataSource legacy;

        public LegacyDataSourceAdapter(LegacyDataSource legacy) {
            this.legacy = legacy;
        }

        public String content() {
            return legacy.fetchRaw();
        }
    }

    // ================= 享元 =================

    /** 享元：字体（family/size/weight 为内部状态，可共享） */
    public static final class Font {
        private final String family;
        private final int size;
        private final String weight;

        Font(String family, int size, String weight) {
            this.family = family;
            this.size = size;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return family + " " + size + "pt " + weight;
        }
    }

    /** 享元工厂：缓存字体，同参数返回同一实例 */
    public static final class FontFactory {
        private static final Map<String, Font> CACHE = new HashMap<>();

        public static Font get(String family, int size, String weight) {
            return CACHE.computeIfAbsent(family + "-" + size + "-" + weight,
                    key -> new Font(family, size, weight));
        }

        public static int cachedCount() {
            return CACHE.size();
        }
    }

    // ================= 装饰器 =================

    /** 导出器接口 */
    public interface Exporter {
        String export(DataSource source, Font font);
    }

    /** 基础导出：纯文本 */
    public static final class PlainTextExporter implements Exporter {
        public String export(DataSource source, Font font) {
            return "[" + font + "] " + source.content();
        }
    }

    /** 装饰器基类 */
    public abstract static class ExporterDecorator implements Exporter {
        protected final Exporter delegate;

        protected ExporterDecorator(Exporter delegate) {
            this.delegate = delegate;
        }
    }

    /** 装饰器：加水印 */
    public static final class WatermarkDecorator extends ExporterDecorator {
        public WatermarkDecorator(Exporter delegate) {
            super(delegate);
        }

        public String export(DataSource source, Font font) {
            return delegate.export(source, font) + "\n[水印: 机密文件]";
        }
    }

    /** 装饰器：加密（Base64 模拟） */
    public static final class EncryptDecorator extends ExporterDecorator {
        public EncryptDecorator(Exporter delegate) {
            super(delegate);
        }

        public String export(DataSource source, Font font) {
            String content = delegate.export(source, font);
            return "[已加密] " + java.util.Base64.getEncoder().encodeToString(content.getBytes());
        }
    }

    /** 装饰器：压缩（GZIP 模拟） */
    public static final class CompressDecorator extends ExporterDecorator {
        public CompressDecorator(Exporter delegate) {
            super(delegate);
        }

        public String export(DataSource source, Font font) {
            return "[已压缩] " + delegate.export(source, font);
        }
    }

    // ================= 外观 =================

    /** 外观：一键导出 */
    public static final class ExportFacade {
        private final Exporter plain = new PlainTextExporter();

        /** 一键普通导出 */
        public String exportPlain(DataSource source, Font font) {
            return plain.export(source, font);
        }

        /** 一键安全导出（加密 + 压缩 + 水印，层层装饰） */
        public String exportSecure(DataSource source, Font font) {
            Exporter exporter = new WatermarkDecorator(
                    new CompressDecorator(
                            new EncryptDecorator(new PlainTextExporter())));
            return exporter.export(source, font);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 文档导出中心（适配器 + 享元 + 装饰器 + 外观） ==========");

        // 适配器：遗留系统也能统一导出
        DataSource internal = new InternalDataSource("订单报表数据");
        DataSource legacy = new LegacyDataSourceAdapter(new LegacyDataSource());

        // 享元：同一字体参数只创建一个对象
        Font font1 = FontFactory.get("宋体", 12, "粗体");
        Font font2 = FontFactory.get("宋体", 12, "粗体");
        System.out.println("  享元复用: 两次 get 同一实例 = " + (font1 == font2)
                + "，缓存 " + FontFactory.cachedCount() + " 个字体");

        // 外观：普通导出
        ExportFacade facade = new ExportFacade();
        System.out.println();
        System.out.println("  普通导出:");
        System.out.println("    " + facade.exportPlain(internal, font1));

        // 外观：安全导出（装饰器叠加：加密 -> 压缩 -> 水印）
        System.out.println("  安全导出（加密+压缩+水印）:");
        System.out.println("    " + facade.exportSecure(legacy, font2));
    }
}
