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
 * 纯 JDK 最小 HTTP 客户端——只用 {@link Socket} 发请求、收响应，配合 HttpRequest/HttpResponse 解析。
 *
 * 这是「HTTP 版本的粘包解决」的完整落地：HTTP/1.1 默认长连接（Keep-Alive），
 * 响应体有多长必须靠头部告诉客户端。本类的核心是 {@link #readResponse}：
 *
 * <pre>
 * 1. 逐字节读响应头，直到 CRLFCRLF（空行）——字节级读，避免 BufferedReader 缓冲吞掉响应体；
 * 2. 从头部解析 Content-Length 或 Transfer-Encoding；
 * 3. Content-Length：按长度精确读响应体（readExactly：可能一次 read 读不全，要循环收齐）；
 * 4. Transfer-Encoding: chunked：按块循环读（大小行 -> 数据 -> CRLF，直到 0 块）；
 * 5. 两者都没有（HTTP/1.0 或 Connection: close）读到 EOF 兜底。
 * </pre>
 *
 * 这就是 TCP 粘包/拆包问题在 HTTP 层的表现与解决：请求和响应都是「头部 + 空行 + 主体」，
 * 主体长度靠 Content-Length（或 chunked 块声明）切分，不会把两条响应的字节混在一起。
 *
 * 运行（配合 {@link MinimalHttpServer}，两个终端）：
 * <pre>
 *   mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.MinimalHttpServer
 *   mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.MinimalHttpClient \
 *       -Dexec.args="127.0.0.1 19080 /hello"
 * </pre>
 * 也可以直接访问公网：`MinimalHttpClient www.example.com 80 /`。
 *
 * 限制（留作练习）：重定向跟随、Cookie 会话、Keep-Alive 连接复用、HTTPS。
 */
public class MinimalHttpClient {

    private static final byte[] BLANK_LINE = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private MinimalHttpClient() {
    }

    /** 发一个 GET 请求（默认带 Host/User-Agent/Connection: close 头）。 */
    public static HttpResponse get(String host, int port, String path) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Host", List.of(host));
        headers.put("User-Agent", List.of("minimal-http-client/1.0"));
        headers.put("Connection", List.of("close"));
        HttpRequest request = new HttpRequest("GET", path, "HTTP/1.1", headers, "");
        return request(host, port, request);
    }

    /** 用一条 TCP 连接发任意请求并读取响应（每请求一条连接，Keep-Alive 复用留作练习）。 */
    public static HttpResponse request(String host, int port, HttpRequest request)
            throws IOException {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10_000); // 兜底：10 秒无数据视为超时，避免挂死
            OutputStream out = socket.getOutputStream();
            out.write(request.encode().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readResponse(socket.getInputStream());
        }
    }

    /**
     * 读取并解析响应：头部逐字节读到空行 -> 按 Content-Length 或 chunked 精确读主体。
     * 这是 HTTP 客户端正确处理「响应边界」的核心（粘包问题的客户端视角）。
     */
    private static HttpResponse readResponse(InputStream in) throws IOException {
        // 1. 逐字节读头部直到 CRLFCRLF（用字节级状态机，不用 BufferedReader——
        //    缓冲流会提前多读主体字节，导致 Content-Length 收不齐）
        byte[] headBytes = readUntilBlankLine(in);
        String head = new String(headBytes, StandardCharsets.UTF_8);
        // 2. 先解析头部（body 暂空），拿到长度声明
        HttpResponse headOnly = HttpResponse.parse(head);
        String contentLength = headOnly.header("Content-Length");
        String transferEncoding = headOnly.header("Transfer-Encoding");
        if (contentLength != null && transferEncoding != null) {
            // 同时声明两个长度机制 = 请求走私攻击特征（RFC 7230 3.3.3），拒绝
            throw new IOException("同时声明 Content-Length 与 Transfer-Encoding，可能是请求走私攻击（RFC 7230）");
        }
        String body;
        if ("chunked".equalsIgnoreCase(transferEncoding)) {
            // 3a. chunked：按块循环读（每块声明自己的长度），直到 0 块
            body = readChunkedBody(in);
        } else if (contentLength != null) {
            // 3b. 按 Content-Length 精确读主体：一次 read 可能读不全，必须循环收齐
            byte[] bodyBytes = readExactly(in, Integer.parseInt(contentLength.trim()));
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        } else {
            // 4. 没有长度声明（HTTP/1.0 或 Connection: close）-> 读到 EOF
            body = new String(readUntilEof(in), StandardCharsets.UTF_8);
        }
        // 完整报文 = 头部 + 主体，交给 HttpResponse 解析
        return HttpResponse.parse(head + body);
    }

    /**
     * 读取 chunked 编码的响应体（RFC 7230 4.1）并解码为完整主体。
     *
     * 格式：每个块 = `[十六进制大小][;扩展]\r\n[数据]\r\n`，最后一个块大小是 0，
     * 0 块之后是可选 trailer 头部，直到空行结束。
     *
     * <pre>
     *   5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n   ->  "hello world"
     * </pre>
     * 与 Content-Length 的区别：每个块**自己声明长度**，服务端可以边生成边发送
     * （流式响应），不必预先知道总长度；这也是 HTTP 层粘包问题的另一种切分方案。
     */
    private static String readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            // 读大小行（可带分号扩展，如 `5;ext=1`，RFC 7230 允许但客户端可忽略）
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
                break; // 最后一个块
            }
            // 读数据 + 块尾 CRLF（字节级，正确处理拆包）
            body.write(readExactly(in, size));
            int cr = in.read();
            int lf = in.read();
            if (cr != '\r' || lf != '\n') {
                throw new IOException("chunk 数据后缺少 CRLF（块边界被破坏）");
            }
        }
        // 0 块之后是可选的 trailer 头部（如校验和），读到空行结束
        while (!readCrlfLine(in).isEmpty()) {
            // 丢弃 trailer 行
        }
        return body.toString(StandardCharsets.UTF_8);
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

    /** 逐字节读取直到出现 CRLFCRLF（含空行），返回完整头部字节。 */
    private static byte[] readUntilBlankLine(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0; // 已匹配到 \r\n\r\n 的字节数
        while (matched < BLANK_LINE.length) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("读取响应头部时连接提前关闭（缺少头部结束空行）");
            }
            head.write(b);
            // 匹配 \r\n\r\n；不匹配时若当前字节是 \r 则从新序列开头继续，否则清零
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

    /** 读到 EOF（用于没有 Content-Length 的响应）。 */
    private static byte[] readUntilEof(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            body.write(buf, 0, n);
        }
        return body.toByteArray();
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
