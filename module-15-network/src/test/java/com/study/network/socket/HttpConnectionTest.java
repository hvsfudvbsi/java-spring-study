package com.study.network.socket;

import com.study.network.packet.HttpRequest;
import com.study.network.packet.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpConnection 集成测试：Keep-Alive 连接复用——同一 Socket 连续发多个请求，
 * 靠 Content-Length/chunked 切分响应边界，最后一条才关闭。
 */
class HttpConnectionTest {

    @Test
    @DisplayName("Keep-Alive 复用：同一连接连发 3 个请求，靠 Content-Length 切分响应")
    void keepAliveMultipleRequests() throws Exception {
        int port = freeTcpPort();
        AtomicInteger count = new AtomicInteger();
        Thread server = startKeepAliveServer(port, head -> {
            int i = count.getAndIncrement();
            return "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nresp" + i;
        });
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertEquals("resp0", conn.request(getRequest("/a")).body());
            assertTrue(conn.isReusable());
            assertEquals("resp1", conn.request(getRequest("/b")).body());
            assertTrue(conn.isReusable());
            assertEquals("resp2", conn.request(getRequest("/c")).body());
            assertTrue(conn.isReusable());
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("Connection: close：读完最后一条响应后连接不可复用，再发请求被拒绝")
    void connectionCloseStopsReuse() throws Exception {
        int port = freeTcpPort();
        Thread server = startKeepAliveServer(port, head ->
                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nhi");
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertEquals("hi", conn.request(getRequest("/")).body());
            assertFalse(conn.isReusable(), "对端声明 Connection: close，连接不再复用");
            assertThrows(IllegalStateException.class, () -> conn.request(getRequest("/")),
                    "不可复用连接再发请求必须报错");
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("HTTP/1.0 无长度响应：读到 EOF 结束，连接随之不可复用")
    void http10EofResponseNotReusable() throws Exception {
        int port = freeTcpPort();
        // HTTP/1.0 无长度声明：服务端发完即关连接，客户端只能读到 EOF 才知道响应结束
        Thread server = startOneShotServer(port,
                "HTTP/1.0 200 OK\r\n\r\nplain body without length".getBytes(StandardCharsets.UTF_8));
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertEquals("plain body without length", conn.request(getRequest("/")).body());
            assertFalse(conn.isReusable(), "无长度声明只能读到 EOF 才知道结束，连接已被对端关闭");
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("chunked 响应有明确边界：读完第一块后还能在同一连接继续发第二个请求")
    void chunkedThenNextRequest() throws Exception {
        int port = freeTcpPort();
        AtomicInteger count = new AtomicInteger();
        Thread server = startKeepAliveServer(port, head -> {
            if (count.getAndIncrement() == 0) {
                return "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                        + "5\r\nhello\r\n0\r\n\r\n";
            }
            return "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok";
        });
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertEquals("hello", conn.request(getRequest("/a")).body());
            assertTrue(conn.isReusable(), "chunked 有块边界，读完不消耗连接");
            assertEquals("ok", conn.request(getRequest("/b")).body());
            assertTrue(conn.isReusable());
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("MinimalHttpServer 配套：同一连接发 4 个请求（200/404/chunked/200）全正确")
    void minimalHttpServerKeepAlive() throws Exception {
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
        awaitPort(port);
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertEquals(200, conn.request(getRequest("/")).statusCode());
            assertEquals(200, conn.request(getRequest("/hello")).statusCode());
            HttpResponse chunked = conn.request(getRequest("/chunked"));
            assertEquals(200, chunked.statusCode());
            assertTrue(chunked.body().contains("分块传输"));
            assertEquals(404, conn.request(getRequest("/nope")).statusCode());
            assertTrue(conn.isReusable(), "请求头未带 Connection: close，连接保持复用");
        } finally {
            server.interrupt();
        }
    }

    /** 构造不带 Connection: close 的 GET 请求（默认 Keep-Alive 语义）。 */
    private static HttpRequest getRequest(String path) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of("127.0.0.1"));
        return new HttpRequest("GET", path, "HTTP/1.1", headers, "");
    }

    /** 一次性服务器：accept 一个连接，读请求头，回响应字节后关闭连接（模拟 HTTP/1.0 发完即关）。 */
    private Thread startOneShotServer(int port, byte[] response) throws IOException {
        ServerSocket server = new ServerSocket(port);
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                readRequestHead(socket);
                socket.getOutputStream().write(response);
                socket.getOutputStream().flush();
                // try-with-resources 关闭连接 -> 客户端读到 EOF
            } catch (IOException e) {
                // 测试结束关闭
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Test
    @DisplayName("gzip 解压：Content-Encoding: gzip 响应体自动解压为原文")
    void gzipDecompressed() throws Exception {
        int port = freeTcpPort();
        String plain = "gzip 压缩的响应体内容 hello world";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(plain.getBytes(StandardCharsets.UTF_8));
        }
        byte[] compressed = bos.toByteArray();
        // 头部 + 压缩字节拼接（Content-Length 是压缩后长度）
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: "
                + compressed.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] full = new byte[head.length + compressed.length];
        System.arraycopy(head, 0, full, 0, head.length);
        System.arraycopy(compressed, 0, full, head.length, compressed.length);

        Thread server = startOneShotServer(port, full);
        try {
            assertEquals(plain, MinimalHttpClient.get("127.0.0.1", port, "/").body(),
                    "压缩响应体被自动解压为原文");
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("管线化：一次写出 3 个请求，依次读回 3 个响应（顺序一致）")
    void pipelineRequests() throws Exception {
        int port = freeTcpPort();
        AtomicInteger count = new AtomicInteger();
        Thread server = startKeepAliveServer(port, head -> {
            int i = count.getAndIncrement();
            return "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nresp" + i;
        });
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            List<HttpResponse> responses = conn.pipeline(List.of(
                    getRequest("/a"), getRequest("/b"), getRequest("/c")));
            assertEquals(3, responses.size());
            assertEquals("resp0", responses.get(0).body());
            assertEquals("resp1", responses.get(1).body());
            assertEquals("resp2", responses.get(2).body());
            assertTrue(conn.isReusable(), "管线化响应都有明确边界，连接保持复用");
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("重定向跟随：302 + Location 自动转到目标，返回最终 200")
    void redirectFollowed() throws Exception {
        int port = freeTcpPort();
        Thread server = startKeepAliveServer(port, head -> {
            if ("/start".equals(requestPath(head))) {
                return "HTTP/1.1 302 Found\r\nLocation: /target\r\nContent-Length: 0\r\n\r\n";
            }
            return "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone";
        });
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            HttpResponse response = conn.requestFollowingRedirects(getRequest("/start"), 5);
            assertEquals(200, response.statusCode());
            assertEquals("done", response.body(), "跟随重定向后拿到最终资源");
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("重定向超限：超过 maxHops 抛异常")
    void redirectTooManyHops() throws Exception {
        int port = freeTcpPort();
        Thread server = startKeepAliveServer(port, head ->
                "HTTP/1.1 302 Found\r\nLocation: /again\r\nContent-Length: 0\r\n\r\n");
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertThrows(IOException.class,
                    () -> conn.requestFollowingRedirects(getRequest("/"), 2));
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("跨主机重定向明确拒绝（需要新建连接，留作练习）")
    void crossHostRedirectRejected() throws Exception {
        int port = freeTcpPort();
        Thread server = startKeepAliveServer(port, head ->
                "HTTP/1.1 302 Found\r\nLocation: http://example.com/x\r\nContent-Length: 0\r\n\r\n");
        try (HttpConnection conn = HttpConnection.connect("127.0.0.1", port)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> conn.requestFollowingRedirects(getRequest("/"), 3));
        } finally {
            server.interrupt();
        }
    }

    /** 从请求头文本提取 URI 路径（第一行「方法 SP 路径 SP 版本」）。 */
    private static String requestPath(String head) {
        return head.split("\r\n")[0].split(" ")[1];
    }

    /** 起一个 Keep-Alive 测试服务器：accept 一次，循环读请求头、回响应，直到 close/EOF。 */
    private Thread startKeepAliveServer(int port, ResponseHandler handler) throws IOException {
        ServerSocket server = new ServerSocket(port);
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                OutputStream out = socket.getOutputStream();
                while (true) {
                    String head = readRequestHead(socket);
                    if (head == null) {
                        return; // 客户端关闭连接
                    }
                    boolean close = head.toLowerCase().contains("connection: close");
                    out.write(handler.handle(head).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    if (close) {
                        return; // 客户端要求关闭
                    }
                }
            } catch (IOException e) {
                // 测试结束关闭
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** 读取请求头直到空行，返回头部文本；EOF 返回 null。 */
    private static String readRequestHead(Socket socket) throws IOException {
        java.io.ByteArrayOutputStream head = new java.io.ByteArrayOutputStream();
        int matched = 0;
        byte[] blank = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        while (matched < blank.length) {
            int b = socket.getInputStream().read();
            if (b == -1) {
                return null;
            }
            head.write(b);
            matched = (b == (blank[matched] & 0xFF)) ? matched + 1
                    : (b == '\r' ? 1 : 0);
        }
        return head.toString(StandardCharsets.UTF_8);
    }

    private interface ResponseHandler {
        String handle(String requestHead) throws IOException;
    }

    private int freeTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 轮询等待服务端端口可连接（最多 2 秒），避免线程启动与端口 bind 的竞态。 */
    private static void awaitPort(int port) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("等待端口 " + port + " 就绪超时");
    }
}
