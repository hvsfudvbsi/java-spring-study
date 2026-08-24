package com.study.network.packet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 响应报文——服务器回给客户端的报文，格式与请求几乎一样，只是第一行换成状态行。
 *
 * 报文结构（HTTP/1.1，行结束符是 CRLF）：
 * <pre>
 *   HTTP/1.1 200 OK\r\n                    <- 状态行：HTTP版本 SP 状态码 SP 原因短语
 *   Content-Type: text/html; charset=utf-8\r\n
 *   Content-Length: 13\r\n                <- 响应体字节数（客户端据此判断响应是否完整）
 *   Set-Cookie: a=1\r\n                   <- 多值头：同一字段名可出现多次
 *   Set-Cookie: b=2\r\n
 *   \r\n                                  <- 空行：头部结束
 *   &lt;h1&gt;Hello&lt;/h1&gt;                      <- 响应体
 * </pre>
 *
 * 状态码语义（面试必背，按百位记）：
 * - 1xx：信息（100 Continue）
 * - 2xx：成功（200 OK、201 Created、204 No Content）
 * - 3xx：重定向（301 永久、302 临时、**304 未修改走缓存**）
 * - 4xx：客户端错误（400 请求格式错误、401 未认证、403 禁止、404 不存在）
 * - 5xx：服务端错误（500 内部错误、502 网关错误、503 服务不可用）
 *
 * 关键理解：
 * - 状态码 3 位数字 + 原因短语，两者配套：客户端程序看状态码，人看原因短语。
 * - 响应体长度由 Content-Length 声明（或 chunked 分块传输），HTTP/1.1 默认 Keep-Alive
 *   复用连接，必须靠长度字段切分响应，否则无法判断响应何时结束（粘包问题的 HTTP 版）。
 * - **多值头**：一个字段名可以出现多次（多个 `Set-Cookie`/`Via`），值按出现顺序追加，
 *   用 `Map<String, List<String>>` 保存；`header()` 取第一个值，`headerValues()` 取全部。
 * - 304 Not Modified：配合 If-None-Match/ETag 让浏览器走缓存，是最常见的性能优化点。
 */
public class HttpResponse {

    private final String version;                        // HTTP 版本，如 HTTP/1.1
    private final int statusCode;                        // 状态码，如 200
    private final String reasonPhrase;                   // 原因短语，如 OK
    private final Map<String, List<String>> headers;     // 响应头（多值，保持写入顺序）
    private final String body;                           // 响应体

    public HttpResponse(String version, int statusCode, String reasonPhrase,
                        Map<String, List<String>> headers, String body) {
        if (version == null || !version.startsWith("HTTP/")) {
            throw new IllegalArgumentException("HTTP 版本必须形如 HTTP/1.1: " + version);
        }
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("状态码必须是 3 位数字（100~599）: " + statusCode);
        }
        this.version = version;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase == null ? reason(statusCode) : reasonPhrase;
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.put(name, new ArrayList<>(values)));
        this.headers = copy;
        this.body = body == null ? "" : body;
    }

    /** 编码为完整报文：状态行 + 头部（每个值一行）+ 空行 + 响应体（行结束符 CRLF）。 */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(version).append(' ').append(statusCode).append(' ')
                .append(reasonPhrase).append("\r\n");
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
     * 从字符串解析 HTTP 响应报文。
     * 以第一个空行（标准 CRLFCRLF，兼容 LFLF）分隔「头部」与「响应体」，
     * 响应体**原样保留**（body 里的 CRLF 属于内容，不能当行分隔符）。
     * 状态行 `HTTP/1.1 200 OK`：版本 + 状态码 + 原因短语（原因短语可以含空格，取剩余部分）。
     * 同名头部按出现顺序追加（多值头）。
     *
     * @throws IllegalArgumentException 缺少头部结束空行、状态行格式错误、状态码非法、头部行缺少冒号
     */
    public static HttpResponse parse(String message) {
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
            throw new IllegalArgumentException("HTTP 响应不能为空");
        }

        // 状态行：HTTP版本 SP 状态码 SP 原因短语（原因短语可含空格）
        String[] parts = lines[0].trim().split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IllegalArgumentException("状态行必须是「HTTP版本 SP 状态码 SP 原因短语」: " + lines[0]);
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("状态码必须是数字: " + parts[1]);
        }
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("状态码必须是 3 位数字（100~599）: " + statusCode);
        }
        String reasonPhrase = parts.length > 2 ? parts[2] : reason(statusCode);

        Map<String, List<String>> headers = parseHeaderLines(lines, 1);
        return new HttpResponse(parts[0], statusCode, reasonPhrase, headers, body);
    }

    /** 解析头部行集合（从第 fromIndex 行开始）：`字段名: 值`，同名头按顺序追加。 */
    private static Map<String, List<String>> parseHeaderLines(String[] lines, int fromIndex) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = fromIndex; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("头部行必须是「字段名: 值」: " + line);
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return headers;
    }

    /** 常见状态码的标准原因短语。 */
    public static String reason(int statusCode) {
        return switch (statusCode) {
            case 100 -> "Continue";
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Status " + statusCode;
        };
    }

    /**
     * 便捷工厂：构造带 Content-Type 与 Content-Length 的文本响应（供最小 HTTP 服务器使用）。
     * 注意：Content-Length 必须是响应体的**字节数**，中文等非 ASCII 字符按 UTF-8 计算。
     */
    public static HttpResponse text(int statusCode, String reasonPhrase, String body) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("text/html; charset=utf-8"));
        headers.put("Content-Length",
                List.of(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length)));
        return new HttpResponse("HTTP/1.1", statusCode, reasonPhrase, headers, body);
    }

    /**
     * 便捷工厂：304 Not Modified 响应——配合 ETag 走缓存（服务器资源未变化时回它，
     * 客户端/浏览器直接用本地缓存，省去重新传输响应体）。
     */
    public static HttpResponse notModified(String etag) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("ETag", List.of(etag));
        return new HttpResponse("HTTP/1.1", 304, null, headers, "");
    }

    /** 拷贝响应并增加/覆盖一个头部（不可变风格，如 Keep-Alive 场景给响应加 Connection 头）。 */
    public HttpResponse withHeader(String name, String value) {
        Map<String, List<String>> newHeaders = new LinkedHashMap<>(headers);
        newHeaders.put(name, List.of(value));
        return new HttpResponse(version, statusCode, reasonPhrase, newHeaders, body);
    }

    /** 大小写不敏感读取单个响应头（返回第一个值），不存在返回 null。 */
    public String header(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                return values.isEmpty() ? null : values.get(0);
            }
        }
        return null;
    }

    /** 大小写不敏感读取响应头的全部值（多值头，如多个 Set-Cookie），不存在返回空列表。 */
    public List<String> headerValues(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    /** 是否成功响应（2xx）。 */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** 状态码所属类别（1xx~5xx）。 */
    public String statusCategory() {
        return switch (statusCode / 100) {
            case 1 -> "信息";
            case 2 -> "成功";
            case 3 -> "重定向";
            case 4 -> "客户端错误";
            case 5 -> "服务端错误";
            default -> "未知";
        };
    }

    public String version() {
        return version;
    }

    public int statusCode() {
        return statusCode;
    }

    public String reasonPhrase() {
        return reasonPhrase;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    @Override
    public String toString() {
        return "HttpResponse{" + version + " " + statusCode + " " + reasonPhrase
                + " (" + statusCategory() + "), headers=" + headers
                + (body.isEmpty() ? "" : ", body='" + body + "'") + '}';
    }
}
