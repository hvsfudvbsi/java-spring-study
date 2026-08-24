package com.study.network.packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求报文——应用层协议 HTTP 的报文格式，后端面试必考。
 *
 * 报文结构（HTTP/1.1，行结束符是 CRLF）：
 * <pre>
 *   GET /index.html HTTP/1.1\r\n          <- 请求行：方法 SP 请求URI SP HTTP版本
 *   Host: www.example.com\r\n             <- 请求头：字段名: 值（字段名不区分大小写）
 *   User-Agent: study-client/1.0\r\n
 *   Cookie: a=1\r\n                       <- 同名头可以出现多次（多值头）
 *   Cookie: b=2\r\n
 *   \r\n                                  <- 空行：头部结束（必须有）
 *   请求体（GET 通常为空，POST 携带表单/JSON）
 * </pre>
 *
 * 关键理解（面试常问）：
 * - 请求行三个部分：方法（GET/POST/PUT/DELETE/HEAD/OPTIONS/PATCH）+ URI + HTTP 版本。
 * - 头部以空行结束：解析器先找到第一个空行，之前是头部、之后是请求体。
 * - 请求体长度由 `Content-Length` 头部声明，服务器用它判断请求体是否完整（防粘包/半包，见粘包章节）。
 * - **多值头**：一个字段名可以出现多次（如多个 `Cookie`、代理链的多个 `Via`），
 *   本类用 `Map&lt;String, List&lt;String&gt;&gt;` 保存全部值并按出现顺序返回。
 * - 头部字段名不区分大小写（`host` 与 `Host` 等价），用 `header(name)` 大小写不敏感读取。
 * - HTTP 无状态：服务器不保存客户端状态，靠 Cookie/Session（见 module-03）在多次请求间维持会话。
 * - 本类只做协议解析（纯 Java，不依赖 Spring/Netty）；真实 HTTP 服务端见 module-11 的 Netty HttpServer。
 */
public class HttpRequest {

    private final String method;                      // 请求方法，如 GET/POST
    private final String uri;                         // 请求 URI，如 /index.html
    private final String version;                     // HTTP 版本，如 HTTP/1.1
    private final Map<String, List<String>> headers;  // 请求头：字段名 -> 全部值（保持写入顺序）
    private final String body;                        // 请求体（GET 通常为空）

    public HttpRequest(String method, String uri, String version,
                       Map<String, List<String>> headers, String body) {
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("请求方法不能为空");
        }
        if (uri == null || uri.isEmpty()) {
            throw new IllegalArgumentException("请求 URI 不能为空");
        }
        if (version == null || !version.startsWith("HTTP/")) {
            throw new IllegalArgumentException("HTTP 版本必须形如 HTTP/1.1: " + version);
        }
        this.method = method;
        this.uri = uri;
        this.version = version;
        this.headers = new LinkedHashMap<>();
        headers.forEach((name, values) -> this.headers.put(name, List.copyOf(values)));
        this.body = body == null ? "" : body;
    }

    /** 编码为完整报文：请求行 + 头部（每个值各一行）+ 空行 + 请求体（行结束符 CRLF）。 */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(uri).append(' ').append(version).append("\r\n");
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            for (String value : header.getValue()) {
                sb.append(header.getKey()).append(": ").append(value).append("\r\n");
            }
        }
        sb.append("\r\n"); // 空行：头部结束
        sb.append(body);
        return sb.toString();
    }

    /**
     * 从字符串解析 HTTP 请求报文。
     * 以第一个空行（标准 CRLFCRLF，兼容 LFLF）分隔「头部」与「请求体」，
     * 请求体**原样保留**（body 里的 CRLF 属于内容，不能当行分隔符）。
     * 同名头部多次出现时值按顺序追加（多值头）。
     *
     * @throws IllegalArgumentException 缺少头部结束空行、请求行格式错误、头部行缺少冒号、Content-Length 与实际请求体不符
     */
    public static HttpRequest parse(String message) {
        int separator = message.indexOf("\r\n\r\n");
        int separatorLength = 4;
        if (separator < 0) {
            separator = message.indexOf("\n\n");
            separatorLength = 2;
        }
        if (separator < 0) {
            throw new IllegalArgumentException("HTTP 报文缺少头部结束空行（CRLFCRLF），报文可能被截断");
        }
        String head = message.substring(0, separator);
        String body = message.substring(separator + separatorLength);
        String[] lines = head.split("\r\n|\n"); // 兼容 CRLF 与 LF 行结束符
        if (lines.length == 0 || lines[0].trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP 请求不能为空");
        }

        // 请求行：方法 SP URI SP 版本（必须 3 段）
        String[] parts = lines[0].trim().split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
            throw new IllegalArgumentException("请求行必须是「方法 SP URI SP HTTP版本」: " + lines[0]);
        }

        Map<String, List<String>> headers = parseHeaderLines(lines, 1);
        // Content-Length 声明应与实际请求体一致（简化：按字符数校验，真实按字节数）
        String contentLength = headerValue(headers, "Content-Length");
        if (contentLength != null) {
            int declared = Integer.parseInt(contentLength.trim());
            if (declared != body.length()) {
                throw new IllegalArgumentException("Content-Length=" + declared
                        + " 与实际请求体长度 " + body.length() + " 不符");
            }
        }
        return new HttpRequest(parts[0], parts[1], parts[2], headers, body);
    }

    /** 解析头部行集合（从第 fromIndex 行开始）：`字段名: 值`，同名头追加到值列表。 */
    private static Map<String, List<String>> parseHeaderLines(String[] lines, int fromIndex) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = fromIndex; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue; // 空行（防御性）
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("头部行必须是「字段名: 值」: " + line);
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return headers;
    }

    /** 大小写不敏感地读取某字段的**第一个**值（无则返回 null）。 */
    public static String headerValue(Map<String, List<String>> headers, String name) {
        List<String> values = headerValues(headers, name);
        return values.isEmpty() ? null : values.get(0);
    }

    /** 大小写不敏感地读取某字段的**全部**值（无则返回空列表）。 */
    public static List<String> headerValues(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase(name)) {
                return header.getValue();
            }
        }
        return List.of();
    }

    /** 大小写不敏感地读取本请求某字段的第一个值（无则 null）。 */
    public String header(String name) {
        return headerValue(headers, name);
    }

    /** 大小写不敏感地读取本请求某字段的全部值（无则空列表）。 */
    public List<String> headerValues(String name) {
        return headerValues(headers, name);
    }

    public String method() {
        return method;
    }

    public String uri() {
        return uri;
    }

    public String version() {
        return version;
    }

    /** 全部头部：字段名 -> 值列表（保持写入顺序，值保持出现顺序）。 */
    public Map<String, List<String>> headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    @Override
    public String toString() {
        return "HttpRequest{" + method + " " + uri + " " + version
                + ", headers=" + headers
                + (body.isEmpty() ? "" : ", body='" + body + "'") + '}';
    }
}
