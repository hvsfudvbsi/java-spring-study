package com.study.network.socket;

import com.study.network.packet.HttpRequest;
import com.study.network.packet.HttpResponse;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 JDK 最小 HTTP 客户端——只用 {@link Socket} 发请求、收响应，配合 HttpRequest/HttpResponse 解析。
 *
 * 这是「HTTP 版本的粘包解决」的完整落地：HTTP/1.1 默认长连接（Keep-Alive），
 * 响应体有多长必须靠头部告诉客户端。响应边界有三种（见 {@link HttpConnection}）：
 * Content-Length 精确读、chunked 按块读、无长度读到 EOF。
 *
 * 本类是**单请求便捷入口**：每次调用开一条连接、发一个请求、读完即关
 * （请求头带 `Connection: close`，最小化且安全）。需要**在同一条连接上连续发多个请求**
 * （Keep-Alive 复用，省去重复三次握手）时，用 {@link HttpConnection}：
 *
 * <pre>
 *   try (HttpConnection conn = HttpConnection.connect(host, port)) {
 *       HttpResponse r1 = conn.request(request1);
 *       HttpResponse r2 = conn.request(request2);  // 复用连接
 *   }
 * </pre>
 *
 * 运行（配合 {@link MinimalHttpServer}，两个终端）：
 * <pre>
 *   mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.MinimalHttpServer
 *   mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.MinimalHttpClient \
 *       -Dexec.args="127.0.0.1 19080 /hello"
 * </pre>
 * 也可以直接访问公网：`MinimalHttpClient www.example.com 80 /`。
 *
 * 限制（留作练习）：重定向跟随、Cookie 会话、HTTPS。
 */
public class MinimalHttpClient {

    private MinimalHttpClient() {
    }

    /** 发一个 GET 请求（默认带 Host/User-Agent/Connection: close 头），单请求一条连接。 */
    public static HttpResponse get(String host, int port, String path) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of(host));
        headers.put("User-Agent", List.of("minimal-http-client/1.0"));
        headers.put("Connection", List.of("close"));
        HttpRequest request = new HttpRequest("GET", path, "HTTP/1.1", headers, "");
        return request(host, port, request);
    }

    /** 发任意请求并读取响应（每请求一条连接，读完即关；复用见 {@link HttpConnection}）。 */
    public static HttpResponse request(String host, int port, HttpRequest request)
            throws IOException {
        try (HttpConnection conn = HttpConnection.connect(host, port)) {
            return conn.request(request);
        }
    }

    /** 命令行入口：MinimalHttpClient [host] [port] [path]，默认 www.example.com:80/。 */
    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "www.example.com";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 80;
        String path = args.length > 2 ? args[2] : "/";
        System.out.println("GET http://" + host + ":" + port + path);
        HttpResponse response = get(host, port, path);
        System.out.println("状态行: " + response.version() + " " + response.statusCode()
                + " " + response.reasonPhrase() + " (" + response.statusCategory() + ")");
        System.out.println("头部:   " + response.headers());
        System.out.println("--- 响应体（" + response.body().length() + " 字符）---");
        System.out.println(response.body());
    }
}
