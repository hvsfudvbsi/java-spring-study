package com.study.network.socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * JDK 原生 TCP 回显服务器（不依赖任何框架，理解 TCP 底层流程）。
 *
 * TCP 服务端核心步骤：
 *   1. new ServerSocket(port)：绑定端口，监听连接
 *   2. accept()：阻塞等待客户端连接，返回 Socket（一个客户端一个连接）
 *   3. 通过 Socket 的 InputStream/OutputStream 读写数据
 *   4. 关闭连接（close 会触发四次挥手）
 *
 * 对比 UDP：
 *   - TCP 必须先 accept 建立连接，UDP 直接收包，无连接
 *   - TCP 是字节流：一次 read 可能读到半条消息或多条消息（粘包/拆包）
 *
 * 运行：先启动本类，再启动 TcpEchoClient（或任意 TCP 客户端）。
 */
public class TcpEchoServer {

    public static final int DEFAULT_PORT = 19001;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT)) {
            System.out.println("TCP 回显服务器启动，监听端口 " + DEFAULT_PORT);
            // 循环 accept：每个客户端连接都开一个线程处理（简单演示）
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("  客户端接入: " + socket.getRemoteSocketAddress());
                new Thread(() -> handle(socket)).start();
            }
        }
    }

    /** 处理单个客户端连接：读一行 -> 原样写回 */
    private static void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("  [服务端] 收到: " + line);
                out.println("echo: " + line);
            }
        } catch (IOException e) {
            System.out.println("  连接处理异常: " + e.getMessage());
        }
        System.out.println("  客户端断开: " + socket.getRemoteSocketAddress());
    }
}
