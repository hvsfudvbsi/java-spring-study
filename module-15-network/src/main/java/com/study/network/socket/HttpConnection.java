package com.study.network.socket;

import com.study.network.packet.HttpRequest;
import com.study.network.packet.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 连接——一条 TCP 连接上的请求/响应会话，支持 **Keep-Alive 连接复用**。
 *
 * 为什么需要它：HTTP/1.1 默认长连接（Keep-Alive），一次 TCP 三次握手可以承载多个请求，
 * 省去每个请求重新握手的开销（高并发下握手成本可观）。复用的前提是**每条响应边界清晰**：
 * 客户端必须知道一个响应在哪结束，才能继续读下一条——这正是本模块粘包问题的 HTTP 版：
 * 靠 Content-Length / chunked 块声明切分，响应之间不会混字节。
 *
 * 什么时候连接不能再复用（{@link #isReusable()} 变 false）：
 * - 响应头带 `Connection: close`（对端明确说完了就关）；
 * - 响应没有长度声明（HTTP/1.0 或 `Connection: close`）——只能读到 EOF 才知道结束，
 *   此时对端已关闭连接；
 * - HTTP/1.0 响应默认连接关闭（除非显式 `Connection: keep-alive`）。
 *
 * 使用方式（与 {@link MinimalHttpServer} 配套，Keep-Alive 复用演示）：
 * <pre>
 *   try (HttpConnection conn = HttpConnection.connect(host, port)) {
 *       HttpResponse r1 = conn.request(request1);   // 同一连接
 *       HttpResponse r2 = conn.request(request2);   // 复用，不再重新握手
 *       ...
 *   }
 * </pre>
 *
 * 限制（留作练习）：无并发（一条连接串行请求）、无管线化（Pipelining，不等响应就连发多个请求）。
 */
public class HttpConnection implements AutoCloseable {

    private static final byte[] BLANK_LINE = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private boolean reusable = true; // 响应以 EOF 结束或对端声明 close 后变 false

    private HttpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(10_000); // 兜底：10 秒无数据视为超时，避免挂死
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /** 建立到 host:port 的连接（默认 HTTP/1.1 Keep-Alive 语义）。 */
    public static HttpConnection connect(String host, int port) throws IOException {
        return new HttpConnection(new Socket(host, port));
    }

    /** 在连接上发一个请求并读取响应。响应边界由 Content-Length/chunked/EOF 决定。 */
    public HttpResponse request(HttpRequest request) throws IOException {
        if (!reusable) {
            throw new IllegalStateException("连接已不可复用（对端已关闭或声明 Connection: close）");
        }
        out.write(request.encode().getBytes(StandardCharsets.UTF_8));
        out.flush();
        return readResponse();
    }

    /** 连接是否还能继续发请求（false = 对端已关闭或本响应是最后一条）。 */
    public boolean isReusable() {
        return reusable;
    }

    /** 关闭底层连接。 */
    @Override
    public void close() throws IOException {
        socket.close();
    }

    /**
     * 读取并解析一个响应，同时判断连接是否还能复用。
     * 步骤：逐字节读头部到空行 -> 按 Content-Length / chunked / EOF 读主体。
     */
    private HttpResponse readResponse() throws IOException {
        byte[] headBytes = readUntilBlankLine(in);
        String head = new String(headBytes, StandardCharsets.UTF_8);
        HttpResponse headOnly = HttpResponse.parse(head);
        String contentLength = headOnly.header("Content-Length");
        String transferEncoding = headOnly.header("Transfer-Encoding");
        if (contentLength != null && transferEncoding != null) {
            throw new IOException("同时声明 Content-Length 与 Transfer-Encoding，可能是请求走私攻击（RFC 7230）");
        }
        String body;
        boolean eofTerminated = false;
        if ("chunked".equalsIgnoreCase(transferEncoding)) {
            body = readChunkedBody(in);
        } else if (contentLength != null) {
            body = new String(readExactly(in, Integer.parseInt(contentLength.trim())),
                    StandardCharsets.UTF_8);
        } else {
            // 没有长度声明：读到 EOF 才知道响应结束，此时对端已关闭连接
            body = new String(readUntilEof(in), StandardCharsets.UTF_8);
            eofTerminated = true;
        }
        HttpResponse response = HttpResponse.parse(head + body);

        // Keep-Alive 判定：EOF 结束、显式 Connection: close、HTTP/1.0 默认关闭
        String connection = headOnly.header("Connection");
        boolean serverWantsClose = "close".equalsIgnoreCase(connection);
        boolean http10DefaultClose = headOnly.version().equals("HTTP/1.0")
                && !"keep-alive".equalsIgnoreCase(connection);
        if (eofTerminated || serverWantsClose || http10DefaultClose) {
            reusable = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // 对端可能已关闭，忽略
            }
        }
        return response;
    }

    /** 读取 chunked 编码的响应体（RFC 7230 4.1）并解码为完整主体。 */
    private static String readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readCrlfLine(in).trim();
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) {
                sizeLine = sizeLine.substring(0, semi);
            }
            int size;
            try {
                size = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                throw new IOException("非法 chunk 大小行: " + sizeLine);
            }
            if (size < 0) {
                throw new IOException("非法 chunk 大小（不能为负）: " + sizeLine);
            }
            if (size == 0) {
                break;
            }
            body.write(readExactly(in, size));
            int cr = in.read();
            int lf = in.read();
            if (cr != '\r' || lf != '\n') {
                throw new IOException("chunk 数据后缺少 CRLF（块边界被破坏）");
            }
        }
        while (!readCrlfLine(in).isEmpty()) {
            // 跳过 0 块之后的 trailer 头部行
        }
        return body.toString(StandardCharsets.UTF_8);
    }

    /** 逐字节读取直到出现 CRLFCRLF（含空行），返回完整头部字节。 */
    private static byte[] readUntilBlankLine(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < BLANK_LINE.length) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("读取响应头部时连接提前关闭（缺少头部结束空行）");
            }
            head.write(b);
            matched = (b == (BLANK_LINE[matched] & 0xFF)) ? matched + 1
                    : (b == '\r' ? 1 : 0);
        }
        return head.toByteArray();
    }

    /** 读取一行以 CRLF 结尾的行（不含 CRLF），字节级实现，避免缓冲吞字节。 */
    private static String readCrlfLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("读取 CRLF 行时连接提前关闭");
            }
            if (prev == '\r' && b == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            line.write(b);
            prev = b;
        }
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

    /** 读到 EOF（用于没有长度声明的响应）。 */
    private static byte[] readUntilEof(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            body.write(buf, 0, n);
        }
        return body.toByteArray();
    }

    /** 命令行入口：连本地 MinimalHttpServer 演示 Keep-Alive 复用（配合服务端，两个终端）。 */
    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 19080;
        try (HttpConnection conn = HttpConnection.connect(host, port)) {
            for (String path : new String[]{"/", "/hello", "/chunked", "/nope"}) {
                Map<String, List<String>> headers = new LinkedHashMap<>();
                headers.put("Host", List.of(host));
                HttpRequest request = new HttpRequest("GET", path, "HTTP/1.1", headers, "");
                HttpResponse response = conn.request(request);
                System.out.println("GET " + path + " -> " + response.statusCode() + " "
                        + response.reasonPhrase() + ", 连接仍可复用: " + conn.isReusable());
            }
        }
    }
}
