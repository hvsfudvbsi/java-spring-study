package com.study.network.socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * JDK 原生 TCP 回显客户端。
 *
 * TCP 客户端核心步骤：
 *   1. new Socket(host, port)：发起连接（触发三次握手）
 *   2. 通过 Socket 读写数据
 *   3. close()：触发四次挥手释放连接
 *
 * 学习点：
 *   - connect 成功 = 三次握手完成，之后才能读写
 *   - Socket 是双向的：既能读也能写（全双工）
 *   - 行协议（readLine/println）只是演示方便；真实 TCP 是字节流，需要帧解码
 *
 * 运行：先启动 TcpEchoServer，再运行本类。
 */
public class TcpEchoClient {

    public static void main(String[] args) throws IOException {
        String response = send("127.0.0.1", TcpEchoServer.DEFAULT_PORT, "你好，TCP");
        System.out.println("TCP 回声: " + response);
    }

    /** 连接服务器、发送一行、读取一行响应 */
    public static String send(String host, int port, String message) throws IOException {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out.println(message);
            return in.readLine();
        }
    }
}
