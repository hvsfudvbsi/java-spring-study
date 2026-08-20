package com.study.network.socket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDK 原生 Socket 集成测试：使用本地回环与随机端口，验证真实网络链路。
 */
class SocketIntegrationTest {

    @Test
    @DisplayName("真实 TCP 链路：客户端发送一行，服务端回声返回")
    void tcpEchoRoundTrip() throws Exception {
        int port = freeTcpPort();
        Thread server = startTcpEchoServer(port);
        try {
            String response = TcpEchoClient.send("127.0.0.1", port, "集成测试消息");
            assertEquals("echo: 集成测试消息", response);
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("真实 UDP 链路：客户端发送数据报，服务端回声返回")
    void udpEchoRoundTrip() throws Exception {
        int port = freeUdpPort();
        Thread server = startUdpEchoServer(port);
        try {
            String response = UdpEchoClient.send("127.0.0.1", port, "UDP 集成测试");
            assertEquals("echo: UDP 集成测试", response);
        } finally {
            server.interrupt();
        }
    }

    @Test
    @DisplayName("TCP 粘包演示：发送 3 条消息但接收方 read 次数小于 3（无边界）")
    void tcpStickyPackets() throws Exception {
        List<String> received = TcpStickyPacketDemo.runTcp();
        // TCP 是字节流，3 条消息可能被合并成更少的 read（本地回环通常 1 次读完全部）
        assertFalse(received.isEmpty());
        assertTrue(received.size() < 3, "TCP 无消息边界，3 条消息不应被拆成 3 次独立 read，实际 " + received.size());
        // 但字节内容完整（粘包 ≠ 丢数据）
        String all = String.join("", received);
        assertTrue(all.contains("消息一") && all.contains("消息二") && all.contains("消息三"),
                "粘包只是边界丢失，数据本身应完整，实际: " + all);
    }

    @Test
    @DisplayName("UDP 数据报演示：发送 3 个数据报，接收方正好收到 3 个（有边界）")
    void udpHasMessageBoundary() throws Exception {
        List<String> received = TcpStickyPacketDemo.runUdp();
        assertEquals(3, received.size(), "UDP 一次 send 对应一次 receive，3 个数据报 = 3 次接收");
        assertEquals("消息一", received.get(0));
        assertEquals("消息二", received.get(1));
        assertEquals("消息三", received.get(2));
    }

    /** 启动一个简单的 TCP 回声服务器（供测试用，独立于 TcpEchoServer 的 while(true) 循环） */
    private Thread startTcpEchoServer(int port) throws IOException {
        ServerSocket server = new ServerSocket(port);
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                String line;
                while ((line = in.readLine()) != null) {
                    out.println("echo: " + line);
                }
            } catch (IOException e) {
                // 测试结束关闭
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** 启动一个简单的 UDP 回声服务器（供测试用） */
    private Thread startUdpEchoServer(int port) throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String data = new String(packet.getData(), packet.getOffset(),
                            packet.getLength(), StandardCharsets.UTF_8);
                    byte[] response = ("echo: " + data).getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(response, response.length, packet.getSocketAddress()));
                }
            } catch (IOException e) {
                // 测试结束关闭
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private int freeTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private int freeUdpPort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
