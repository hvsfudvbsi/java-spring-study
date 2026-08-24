package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HTTP 请求报文测试：验证请求行、头部（含多值头）、请求体与 Content-Length 的编解码。
 */
class HttpRequestTest {

    /** 便捷构造：`name1, v1, name2, v2...` -> 每个字段单个值。 */
    private static Map<String, List<String>> headers(String... kv) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], List.of(kv[i + 1]));
        }
        return map;
    }

    @Test
    @DisplayName("GET 请求往返：请求行 + 头部 + 空行，编码后能完整解析回来")
    void getRequestRoundTrip() {
        HttpRequest request = new HttpRequest("GET", "/index.html", "HTTP/1.1",
                headers("Host", "www.example.com", "User-Agent", "study-client/1.0"), "");

        String text = request.encode();
        assertEquals("GET /index.html HTTP/1.1\r\n"
                + "Host: www.example.com\r\n"
                + "User-Agent: study-client/1.0\r\n"
                + "\r\n", text, "报文用 CRLF 结尾、空行分隔头部与请求体");

        HttpRequest parsed = HttpRequest.parse(text);
        assertEquals("GET", parsed.method());
        assertEquals("/index.html", parsed.uri());
        assertEquals("HTTP/1.1", parsed.version());
        assertEquals("www.example.com", parsed.header("Host"));
        assertEquals("study-client/1.0", parsed.header("User-Agent"));
        assertEquals("", parsed.body());
    }

    @Test
    @DisplayName("POST 请求带请求体：Content-Length 与实际长度一致，解析出 body")
    void postRequestWithBody() {
        String body = "name=alice&age=30"; // 17 个字符
        HttpRequest request = new HttpRequest("POST", "/login", "HTTP/1.1",
                headers("Host", "www.example.com", "Content-Type",
                        "application/x-www-form-urlencoded", "Content-Length", "17"),
                body);

        HttpRequest parsed = HttpRequest.parse(request.encode());
        assertEquals("POST", parsed.method());
        assertEquals("/login", parsed.uri());
        assertEquals(body, parsed.body());
        assertEquals("17", parsed.header("Content-Length"), "大小写不敏感读取头部");
    }

    // ---- 多值头 ----

    @Test
    @DisplayName("多个 Cookie 头往返：每个值各占一行，解析后按顺序全部保留")
    void multipleCookieHeadersRoundTrip() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("www.example.com"));
        headers.put("Cookie", new ArrayList<>(List.of("session=abc123", "theme=dark")));
        HttpRequest request = new HttpRequest("GET", "/", "HTTP/1.1", headers, "");

        String text = request.encode();
        assertEquals("GET / HTTP/1.1\r\n"
                + "Host: www.example.com\r\n"
                + "Cookie: session=abc123\r\n"
                + "Cookie: theme=dark\r\n"
                + "\r\n", text, "同名头每个值各输出一行");

        HttpRequest parsed = HttpRequest.parse(text);
        assertEquals(List.of("session=abc123", "theme=dark"),
                parsed.headerValues("Cookie"), "多值头按出现顺序全部保留");
        assertEquals(1, parsed.headerValues("Host").size());
    }

    @Test
    @DisplayName("同名头部多次出现：解析时值追加而不是覆盖")
    void duplicateHeadersAppend() {
        HttpRequest parsed = HttpRequest.parse(
                "GET / HTTP/1.1\r\nCookie: a=1\r\nCookie: b=2\r\nCookie: c=3\r\n\r\n");
        assertEquals(List.of("a=1", "b=2", "c=3"), parsed.headerValues("Cookie"));
    }

    @Test
    @DisplayName("header 返回第一个值（兼容单值场景），headerValues 大小写不敏感")
    void headerAccessors() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Cookie", List.of("a=1", "b=2"));
        HttpRequest request = new HttpRequest("GET", "/", "HTTP/1.1", headers, "");

        assertEquals("a=1", request.header("Cookie"), "header 返回第一个值");
        assertEquals("a=1", request.header("COOKIE"), "字段名不区分大小写");
        assertEquals(List.of("a=1", "b=2"), request.headerValues("cookie"));
        assertNull(request.header("X-Not-Exist"));
        assertEquals(List.of(), request.headerValues("X-Not-Exist"), "不存在的字段返回空列表");
    }

    // ---- 其它 ----

    @Test
    @DisplayName("兼容 LF 换行：解析器接受不带 \\r 的报文（真实网络是 CRLF）")
    void tolerantOfLfOnly() {
        HttpRequest parsed = HttpRequest.parse(
                "GET / HTTP/1.1\nHost: a.com\n\nbody-here");
        assertEquals("GET", parsed.method());
        assertEquals("a.com", parsed.header("Host"));
        assertEquals("body-here", parsed.body());
    }

    @Test
    @DisplayName("头部顺序保持写入顺序（LinkedHashMap）")
    void headerOrderPreserved() {
        HttpRequest request = new HttpRequest("GET", "/", "HTTP/1.1",
                headers("A", "1", "B", "2", "C", "3"), "");
        HttpRequest parsed = HttpRequest.parse(request.encode());
        assertEquals(List.of("A", "B", "C"),
                new ArrayList<>(parsed.headers().keySet()), "解析后仍保持写入顺序");
    }

    @Test
    @DisplayName("非法请求行被拒绝：缺 URI、版本不是 HTTP/")
    void malformedRequestLineRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.parse("GET HTTP/1.1\r\n\r\n"), "缺少 URI");
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.parse("GET / FOO/1.1\r\n\r\n"), "版本不是 HTTP/");
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.parse(""), "空报文");
    }

    @Test
    @DisplayName("非法头部行被拒绝：没有冒号")
    void malformedHeaderLineRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpRequest.parse("GET / HTTP/1.1\r\nBadHeaderNoColon\r\n\r\n"));
    }

    @Test
    @DisplayName("Content-Length 与请求体不符被拒绝")
    void contentLengthMismatchRejected() {
        String text = "POST / HTTP/1.1\r\nContent-Length: 99\r\n\r\nshort";
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.parse(text));
    }
}
