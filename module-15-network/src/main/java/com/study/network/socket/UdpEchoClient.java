package com.study.network.socket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * JDK 原生 UDP 回显客户端。
 *
 * UDP 客户端核心步骤：
 *   1. new DatagramSocket()：随机绑定一个本地端口（不指定）
 *   2. 构造 DatagramPacket(数据, 长度, 目标地址, 目标端口)
 *   3. send / receive
 *
 * 对比 TCP：
 *   - 不需要 connect（虽然 UDP 也有 connect 方法，但只是"记录对端"，不建连接）
 *   - 一次 send 对应一次 receive：数据报有边界
 *   - send 成功只代表"数据交给了网卡"，不代表对方收到（无确认）
 *
 * 运行：先启动 UdpEchoServer，再运行本类。
 */
public class UdpEchoClient {

    public static void main(String[] args) throws Exception {
        String response = send("127.0.0.1", UdpEchoServer.DEFAULT_PORT, "你好，UDP");
        System.out.println("UDP 回声: " + response);
    }

    /** 发送一个数据报并等待回显 */
    public static String send(String host, int port, String message) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] request = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    request, request.length, InetAddress.getByName(host), port);
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            return new String(reply.getData(), reply.getOffset(), reply.getLength(),
                    StandardCharsets.UTF_8);
        }
    }
}
