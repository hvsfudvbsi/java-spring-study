package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HTTP 请求报文测试：验证请求行、头部、请求体与 Content-Length 的编解码。
 */
class HttpRequestTest {

    private static Map<String, String> headers(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
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
        assertEquals("www.example.com", parsed.headers().get("Host"));
        assertEquals("study-client/1.0", parsed.headers().get("User-Agent"));
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

    @Test
    @DisplayName("头部字段名不区分大小写：Host 与 host 等价")
    void headerCaseInsensitive() {
        HttpRequest request = new HttpRequest("GET", "/", "HTTP/1.1",
                headers("Host", "www.example.com"), "");
        assertEquals("www.example.com", request.header("HOST"));
        assertEquals("www.example.com", request.header("host"));
        assertNull(request.header("X-Not-Exist"));
    }

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
        assertEquals(java.util.List.of("A", "B", "C"),
                new java.util.ArrayList<>(parsed.headers().keySet()), "解析后仍保持写入顺序");
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
