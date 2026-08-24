package com.study.network.socket;

import com.study.network.packet.HttpRequest;
import com.study.network.packet.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 纯 JDK 最小 HTTP 服务器——只用 {@link ServerSocket} 收请求、回响应，配合 HttpRequest/HttpResponse 解析。
 *
 * 与 {@link MinimalHttpClient} 互为镜像，共同演示「HTTP 的粘包解决」：
 * 服务端逐字节读请求头直到 CRLFCRLF，从头部解析 Content-Length 再精确读请求体
 * （{@link #readRequest}），响应则用 {@link HttpResponse#encode()} 序列化——
 * 响应体长度由 Content-Length 声明，客户端据此切分，两条响应不会混在一起。
 *
 * 限制（留作练习）：只支持 GET、单线程串行 accept（一次只处理一个连接）、
 * 无 Keep-Alive 复用、无 chunked、无静态文件目录遍历防护。真实服务器用 Netty（module-11）。
 */
public class MinimalHttpServer {

    private static final byte[] BLANK_LINE = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final int port;

    public MinimalHttpServer(int port) {
        this.port = port;
    }

    /**
     * 启动并阻塞服务：单线程循环 accept，读一个请求、回一个响应。
     * 单个连接异常（客户端提前断开、请求格式错误）不会影响继续服务。
     * 停止方式：Ctrl+C 或中断线程。
     */
    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("MinimalHttpServer 监听 http://127.0.0.1:" + port + " (Ctrl+C 退出)");
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = server.accept()) {
                    handle(socket);
                } catch (IOException | IllegalArgumentException e) {
                    // 单个坏连接（如探测连接提前关闭）不影响服务器继续监听
                    System.out.println("连接异常（继续服务）: " + e.getMessage());
                }
            }
        }
    }

    /** 处理一个连接：读请求 -> 路由 -> 回响应。 */
    private void handle(Socket socket) throws IOException {
        HttpRequest request = readRequest(socket.getInputStream());
        System.out.println("收到: " + request.method() + " " + request.uri() + " (Host="
                + request.header("Host") + ")");
        HttpResponse response = route(request);
        OutputStream out = socket.getOutputStream();
        out.write(response.encode().getBytes(StandardCharsets.UTF_8));
        out.flush();
        System.out.println("回应: " + response.statusCode() + " "
                + response.reasonPhrase() + " (" + response.body().length() + " 字节)");
    }

    /** 简单路由：/ 与 /hello 返回 HTML，其余 404。 */
    private HttpResponse route(HttpRequest request) {
        String path = request.uri();
        if (!"GET".equals(request.method())) {
            return HttpResponse.text(405, "Method Not Allowed", "只支持 GET\n");
        }
        if ("/".equals(path) || "/hello".equals(path)) {
            return HttpResponse.text(200, "OK",
                    "<h1>Hello from MinimalHttpServer</h1>\n<p>你请求了 " + path + "</p>\n");
        }
        return HttpResponse.text(404, "Not Found", "404 页面不存在: " + path + "\n");
    }

    /** 读取一个请求：头部逐字节读到空行 -> 按 Content-Length 精确读请求体。 */
    private HttpRequest readRequest(InputStream in) throws IOException {
        byte[] headBytes = readUntilBlankLine(in);
        String head = new String(headBytes, StandardCharsets.UTF_8);
        HttpRequest headOnly = HttpRequest.parse(head);
        String contentLength = headOnly.header("Content-Length");
        String body = "";
        if (contentLength != null) {
            byte[] bodyBytes = readExactly(in, Integer.parseInt(contentLength.trim()));
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        }
        return HttpRequest.parse(head + body);
    }

    /** 逐字节读取直到出现 CRLFCRLF（含空行），返回完整头部字节。 */
    private static byte[] readUntilBlankLine(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0; // 已匹配到 \r\n\r\n 的字节数
        while (matched < BLANK_LINE.length) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("读取请求头部时连接提前关闭（缺少头部结束空行）");
            }
            head.write(b);
            matched = (b == (BLANK_LINE[matched] & 0xFF)) ? matched + 1
                    : (b == '\r' ? 1 : 0);
        }
        return head.toByteArray();
    }

    /** 精确读取 length 字节（循环 read 直到收齐，处理拆包/半包）。 */
    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(buf, read, length - read);
            if (n == -1) {
                throw new IOException("连接提前关闭，期望 " + length + " 字节主体，实际收到 " + read);
            }
            read += n;
        }
        return buf;
    }

    /** 命令行入口：MinimalHttpServer [port]，默认 19080。 */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 19080;
        new MinimalHttpServer(port).start();
    }
}
