package com.study.network.socket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * JDK 原生 UDP 回显服务器。
 *
 * UDP 服务端核心步骤：
 *   1. new DatagramSocket(port)：绑定端口
 *   2. receive(packet)：阻塞接收数据报（自带发送方地址）
 *   3. 构造新的 DatagramPacket 发回发送方
 *
 * 对比 TCP：
 *   - 无 accept、无连接：一个 DatagramSocket 就能服务所有客户端
 *   - receive 一次收到一个完整数据报（有消息边界，无粘包拆包问题）
 *   - 数据报可能丢失、乱序、重复：本例是回显，丢了就丢了
 *
 * 运行：先启动本类，再启动 UdpEchoClient。
 */
public class UdpEchoServer {

    public static final int DEFAULT_PORT = 19002;
    /** 数据报大小上限：UDP 负载一般不超过 65507 字节 */
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(DEFAULT_PORT)) {
            System.out.println("UDP 回显服务器启动，监听端口 " + DEFAULT_PORT);
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String data = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("  收到来自 " + packet.getSocketAddress() + ": " + data);

                // 原样回显：目标地址 = 发送方地址（packet 自带）
                byte[] response = ("echo: " + data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        response, response.length, packet.getSocketAddress());
                socket.send(reply);
            }
        }
    }
}
