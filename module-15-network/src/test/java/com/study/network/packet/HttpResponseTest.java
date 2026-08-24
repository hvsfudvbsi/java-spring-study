package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 响应报文测试：验证状态行、状态码语义、头部与响应体的编解码。
 */
class HttpResponseTest {

    private static Map<String, String> headers(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("200 OK 响应往返：状态行 + 头部 + 空行 + 响应体")
    void okResponseRoundTrip() {
        HttpResponse response = new HttpResponse("HTTP/1.1", 200, "OK",
                headers("Content-Type", "text/html; charset=utf-8", "Content-Length", "13"),
                "<h1>Hello</h1>");

        String text = response.encode();
        assertEquals("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: 13\r\n"
                + "\r\n"
                + "<h1>Hello</h1>", text);

        HttpResponse parsed = HttpResponse.parse(text);
        assertEquals("HTTP/1.1", parsed.version());
        assertEquals(200, parsed.statusCode());
        assertEquals("OK", parsed.reasonPhrase());
        assertEquals("text/html; charset=utf-8", parsed.headers().get("Content-Type"));
        assertEquals("<h1>Hello</h1>", parsed.body());
        assertTrue(parsed.isSuccess());
        assertEquals("成功", parsed.statusCategory());
    }

    @Test
    @DisplayName("原因短语含空格：HTTP/1.1 404 Not Found 解析正确")
    void reasonPhraseWithSpaces() {
        HttpResponse parsed = HttpResponse.parse(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n");
        assertEquals(404, parsed.statusCode());
        assertEquals("Not Found", parsed.reasonPhrase(), "原因短语可以含空格");
        assertFalse(parsed.isSuccess());
        assertEquals("客户端错误", parsed.statusCategory());
    }

    @Test
    @DisplayName("常见状态码原因短语：200/201/301/304/401/403/500/503")
    void statusReasonMapping() {
        assertEquals("OK", HttpResponse.reason(200));
        assertEquals("Created", HttpResponse.reason(201));
        assertEquals("Moved Permanently", HttpResponse.reason(301));
        assertEquals("Not Modified", HttpResponse.reason(304));
        assertEquals("Unauthorized", HttpResponse.reason(401));
        assertEquals("Forbidden", HttpResponse.reason(403));
        assertEquals("Internal Server Error", HttpResponse.reason(500));
        assertEquals("Service Unavailable", HttpResponse.reason(503));
    }

    @Test
    @DisplayName("不传原因短语时自动补标准短语：状态码 201 默认 Created")
    void reasonAutoFilled() {
        HttpResponse response = new HttpResponse("HTTP/1.1", 201, null,
                headers("Content-Length", "0"), "");
        assertEquals("Created", response.reasonPhrase());
        assertEquals("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n", response.encode());
    }

    @Test
    @DisplayName("2xx 成功 / 3xx 重定向 / 5xx 失败判断正确")
    void successClassification() {
        assertTrue(new HttpResponse("HTTP/1.1", 204, null, headers(), "").isSuccess());
        assertFalse(new HttpResponse("HTTP/1.1", 302, null, headers(), "").isSuccess());
        assertFalse(new HttpResponse("HTTP/1.1", 500, null, headers(), "").isSuccess());
        assertEquals("重定向", new HttpResponse("HTTP/1.1", 302, null, headers(), "")
                .statusCategory());
        assertEquals("服务端错误", new HttpResponse("HTTP/1.1", 503, null, headers(), "")
                .statusCategory());
    }

    @Test
    @DisplayName("非法状态行被拒绝：缺状态码、状态码不是数字、超出 100~599")
    void malformedStatusLineRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpResponse.parse("HTTP/1.1 OK\r\n\r\n"), "缺状态码");
        assertThrows(IllegalArgumentException.class,
                () -> HttpResponse.parse("HTTP/1.1 abc OK\r\n\r\n"), "状态码不是数字");
        assertThrows(IllegalArgumentException.class,
                () -> HttpResponse.parse("HTTP/1.1 99 OK\r\n\r\n"), "状态码超出范围");
        assertThrows(IllegalArgumentException.class,
                () -> new HttpResponse("HTTP/1.1", 600, "X", headers(), ""), "构造参数非法");
    }

    @Test
    @DisplayName("响应体含 CRLF 仍能完整解析（Content-Type 等头部之后的所有内容都是 body）")
    void bodyWithNewlines() {
        HttpResponse response = new HttpResponse("HTTP/1.1", 200, "OK",
                headers("Content-Type", "text/plain"), "line1\r\nline2\r\nline3");
        HttpResponse parsed = HttpResponse.parse(response.encode());
        assertEquals("line1\r\nline2\r\nline3", parsed.body(), "body 可以包含换行");
    }
}
