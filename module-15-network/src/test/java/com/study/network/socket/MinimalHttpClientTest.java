package com.study.network.socket;

import com.study.network.packet.HttpRequest;
import com.study.network.packet.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯 JDK 最小 HTTP 客户端集成测试：用本地 ServerSocket 模拟真实服务端，
 * 验证「按 Content-Length 切分响应」这一 HTTP 粘包解决方案的完整链路。
 */
class MinimalHttpClientTest {

    @Test
    @DisplayName("真实链路：GET 请求发到本地服务端，按 Content-Length 收到完整响应")
    void getRoundTripWithContentLength() throws Exception {
        int port = freeTcpPort();
        Thread server = startServer(port, socket -> {
            // 读到空行即视为请求头结束（测试只需确认请求到达）
            readRequestHead(socket);
            String body = "<h1>你好, HTTP</h1>";
            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                    + "\r\n" + body;
            socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        });
        try {
            HttpResponse response = MinimalHttpClient.get("127.0.0.1", port, "/hello");
            assertEquals(200, response.statusCode());
            assertEquals("OK", response.reasonPhrase());
            assertEquals("<h1>你好, HTTP</h1>", response.body());
            assertTrue(response.isSuccess());
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("拆包场景：服务端分 5 次写响应体，客户端 readExactly 循环收齐")
    void fragmentedBodyIsAssembled() throws Exception {
        int port = freeTcpPort();
        Thread server = startServer(port, socket -> {
            readRequestHead(socket);
            String body = "0123456789ABCDEF"; // 16 字节
            String head = "HTTP/1.1 200 OK\r\nContent-Length: 16\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            // 响应体故意分成 5 小片发送（每次 1~4 字节），模拟网络拆包
            for (int i = 0; i < body.length(); ) {
                int chunk = Math.min(1 + (i % 4), body.length() - i);
                out.write(body.substring(i, i + chunk).getBytes(StandardCharsets.US_ASCII));
                i += chunk;
            }
            out.flush();
        });
        try {
            HttpResponse response = MinimalHttpClient.get("127.0.0.1", port, "/");
            assertEquals(16, response.body().length());
            assertEquals("0123456789ABCDEF", response.body());
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("无 Content-Length（HTTP/1.0 或 Connection: close）：读到 EOF 兜底")
    void noContentLengthReadsUntilEof() throws Exception {
        int port = freeTcpPort();
        Thread server = startServer(port, socket -> {
            readRequestHead(socket);
            // 故意不给 Content-Length，连接关闭即响应结束（HTTP/1.0 语义）
            socket.getOutputStream().write(("HTTP/1.0 200 OK\r\n\r\n"
                    + "streamed body without length").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.close();
        });
        try {
            HttpResponse response = MinimalHttpClient.get("127.0.0.1", port, "/");
            assertEquals("streamed body without length", response.body());
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("chunked 传输编码：明确拒绝并提示（留作练习）")
    void chunkedIsRejected() throws Exception {
        int port = freeTcpPort();
        Thread server = startServer(port, socket -> {
            readRequestHead(socket);
            String response = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                    + "5\r\nhello\r\n0\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        });
        try {
            UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                    () -> MinimalHttpClient.get("127.0.0.1", port, "/"));
            assertTrue(ex.getMessage().contains("chunked"));
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("MinimalHttpServer 配套：真实服务端处理请求并回 404/200")
    void minimalHttpServerRoundTrip() throws Exception {
        int port = freeTcpPort();
        MinimalHttpServer httpServer = new MinimalHttpServer(port);
        Thread server = new Thread(() -> {
            try {
                httpServer.start();
            } catch (IOException e) {
                // 测试结束关闭
            }
        });
        server.setDaemon(true);
        server.start();
        try {
            // 200 路径
            HttpResponse ok = MinimalHttpClient.get("127.0.0.1", port, "/hello");
            assertEquals(200, ok.statusCode());
            assertTrue(ok.body().contains("Hello from MinimalHttpServer"));
            // 404 路径
            HttpResponse notFound = MinimalHttpClient.get("127.0.0.1", port, "/nope");
            assertEquals(404, notFound.statusCode());
            assertTrue(notFound.body().contains("404"));
            // POST 被拒绝
            Map<String, List<String>> headers = new LinkedHashMap<>();
            headers.put("Host", List.of("127.0.0.1"));
            HttpRequest post = new HttpRequest("POST", "/hello", "HTTP/1.1", headers, "x=1");
            HttpResponse methodNotAllowed = MinimalHttpClient.request("127.0.0.1", port, post);
            assertEquals(405, methodNotAllowed.statusCode());
        } finally {
            server.interrupt();
        }
    }

    /** 读取请求头直到空行（测试用：确认请求到达即可，不解析细节）。 */
    private static void readRequestHead(Socket socket) throws IOException {
        int matched = 0;
        byte[] blank = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        while (matched < blank.length) {
            int b = socket.getInputStream().read();
            if (b == -1) {
                return;
            }
            matched = (b == (blank[matched] & 0xFF)) ? matched + 1
                    : (b == '\r' ? 1 : 0);
        }
    }

    /** 起一个一次性 TCP 服务器线程（处理单个连接后退出）。 */
    private Thread startServer(int port, SocketHandler handler) throws IOException {
        ServerSocket server = new ServerSocket(port);
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                handler.handle(socket);
            } catch (IOException e) {
                // 测试结束关闭
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private interface SocketHandler {
        void handle(Socket socket) throws IOException;
    }

    private int freeTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
