package com.study.designpattern.creational;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 建造者模式（Builder）用例（常用 + 不常用）
 *
 * 把"复杂对象的构建过程"与"对象本身"分离，用链式调用一步步组装，最后 build() 产出不可变对象。
 * 适用：构造参数多且大部分可选、参数之间有约束校验、要产出不可变对象。
 *
 * 面试必问：为什么不用"构造器重载 + setter"？
 *   1. 构造器参数多了难读（几个 boolean 参数谁分得清？）
 *   2. setter 破坏不可变性（对象 new 出来后还能被改）
 *   3. Builder 可以在 build() 时统一做参数校验，杜绝"半成品对象"
 */
public class BuilderDemo {

    /** 目标：不可变 HTTP 请求对象（url 必填，其余可选） */
    public static final class HttpRequest {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private final String body;

        private HttpRequest(Builder builder) {
            this.url = builder.url;
            this.method = builder.method;
            this.headers = Map.copyOf(builder.headers);   // 防御性拷贝：外部改 map 不影响本对象
            this.body = builder.body;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String url() {
            return url;
        }

        public String method() {
            return method;
        }

        public Map<String, String> headers() {
            return headers;
        }

        public String body() {
            return body;
        }

        @Override
        public String toString() {
            return "HttpRequest{" + method + " " + url + ", headers=" + headers + ", body=" + body + "}";
        }

        /** 链式建造者 */
        public static final class Builder {
            private String url;
            private String method = "GET";
            private final Map<String, String> headers = new LinkedHashMap<>();
            private String body;

            public Builder url(String url) {
                this.url = url;
                return this;
            }

            public Builder method(String method) {
                this.method = method;
                return this;
            }

            public Builder header(String key, String value) {
                headers.put(key, value);
                return this;
            }

            public Builder body(String body) {
                this.body = body;
                return this;
            }

            /** 统一校验：必填项缺失直接失败 */
            public HttpRequest build() {
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException("url 是必填项");
                }
                return new HttpRequest(this);
            }
        }
    }

    /** 不常用：record + Builder（record 不可变，Builder 负责带默认值的组装，withXxx 负责拷贝修改） */
    public record Config(String host, int port, boolean tls, int timeoutMs) {
        public static ConfigBuilder builder() {
            return new ConfigBuilder();
        }

        public Config withPort(int port) {
            return new Config(host, port, tls, timeoutMs);
        }

        public static final class ConfigBuilder {
            private String host = "localhost";
            private int port = 8080;
            private boolean tls = false;
            private int timeoutMs = 3000;

            public ConfigBuilder host(String host) {
                this.host = host;
                return this;
            }

            public ConfigBuilder port(int port) {
                this.port = port;
                return this;
            }

            public ConfigBuilder tls(boolean tls) {
                this.tls = tls;
                return this;
            }

            public ConfigBuilder timeoutMs(int timeoutMs) {
                this.timeoutMs = timeoutMs;
                return this;
            }

            public Config build() {
                return new Config(host, port, tls, timeoutMs);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 建造者：常用写法（链式 + build 校验） ==========");
        HttpRequest request = HttpRequest.builder()
                .url("https://api.example.com/orders")
                .method("POST")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer xxx")
                .body("{\"id\":1}")
                .build();
        System.out.println("  " + request);
        try {
            HttpRequest.builder().build();
        } catch (IllegalStateException e) {
            System.out.println("  缺 url 时 build() 拦截: " + e.getMessage());
        }

        System.out.println();
        System.out.println("========== 建造者：不常用写法 ==========");
        Config config = Config.builder().host("db.internal").port(5432).tls(true).build();
        System.out.println("  record + Builder: " + config);
        System.out.println("  record 拷贝修改 withPort(3306): " + config.withPort(3306));

        // JDK 内置建造者
        String joined = Stream.<String>builder().add("a").add("b").build().reduce("", String::concat);
        StringBuilder sb = new StringBuilder().append("StringBuilder").append(" 也是建造者");
        System.out.println("  JDK 内置: Stream.builder=" + joined + "；" + sb);
    }
}
