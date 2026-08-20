package com.study.network.socket.framed;

import com.study.network.socket.framed.FrameCodec.FrameDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * 带长度头帧协议的多线程 TCP 服务器。
 *
 * 对比 module-15 的 TcpEchoServer（行协议）：
 * - TcpEchoServer 用 readLine 依赖换行符分隔，内容不能含换行
 * - 本服务器用 [4 字节长度][内容] 帧协议，可传输任意内容，不受分隔符限制
 * - 这对应 Netty 的 LengthFieldBasedFrameDecoder 思路
 *
 * 多线程模型：
 * - 主线程：accept 循环，每来一个客户端 new Thread 处理
 * - 工作线程：循环 read -> FrameDecoder 拆帧 -> 逐帧回声
 * - 优点：一个连接阻塞不影响其他连接；缺点：线程数随连接数增长（学习用足够）
 *
 * 运行：先启动本类，再启动 FramedTcpClient。
 */
public class FramedTcpServer {

    public static final int DEFAULT_PORT = 19003;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT)) {
            System.out.println("帧协议服务器启动（[4字节长度][内容]），监听端口 " + DEFAULT_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("  客户端接入: " + socket.getRemoteSocketAddress());
                // 每连接一个线程：读拆帧、逐帧回声
                new Thread(() -> handle(socket)).start();
            }
        }
    }

    /** 处理单个连接：循环读取字节 -> 帧解码器拆帧 -> 逐帧回声 */
    private static void handle(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            FrameDecoder decoder = new FrameDecoder();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                // 1. 把本批字节喂给解码器，拆出完整帧（粘包时一帧一帧吐出来）
                List<String> frames = decoder.decode(java.util.Arrays.copyOf(buffer, count));
                // 2. 逐帧回声（用帧协议编码，保证回声也有边界）
                for (String frame : frames) {
                    System.out.println("  [服务端] 收到完整帧: " + frame);
                    out.write(FrameCodec.encode("echo: " + frame));
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.out.println("  连接处理异常: " + e.getMessage());
        }
        System.out.println("  客户端断开: " + socket.getRemoteSocketAddress());
    }
}
