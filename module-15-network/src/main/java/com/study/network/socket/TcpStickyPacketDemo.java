package com.study.network.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP 粘包/拆包演示——理解"字节流 vs 数据报"的关键实验。
 *
 * 场景：发送方连续 send 3 条消息，观察接收方收到几条。
 * - TCP：底层是字节流，**没有消息边界**。接收方一次 read 可能收到：
 *   - 粘包：3 条消息合在一起（接收方只 read 到 1 次）
 *   - 拆包：1 条消息被拆成多段（网络延迟时）
 *   所以应用层必须自己定义帧格式（长度头/分隔符）——这正是 Netty 解码器解决的问题。
 * - UDP：一次 send 对应一次 receive，**天然有边界**，不会粘包。
 *
 * 本类用本地回环模拟，方法返回实际收到的数据，便于测试断言。
 */
public class TcpStickyPacketDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== TCP 粘包演示（字节流无边界） ==========");
        List<String> tcpReceived = runTcp();
        System.out.println("TCP 发送 3 条消息，接收方一次 read 读到 " + tcpReceived.size() + " 次");
        for (String s : tcpReceived) {
            System.out.println("  -> " + s);
        }
        System.out.println("  结论: TCP 没有消息边界，应用层必须自定义帧格式");

        System.out.println();
        System.out.println("========== UDP 数据报演示（天然有边界） ==========");
        List<String> udpReceived = runUdp();
        System.out.println("UDP 发送 3 个数据报，接收方收到 " + udpReceived.size() + " 个");
        for (String s : udpReceived) {
            System.out.println("  -> " + s);
        }
        System.out.println("  结论: UDP 一次 send 对应一次 receive，不会粘包");
    }

    /**
     * TCP：发送 3 条消息，接收方**一次 read 完所有可用数据**（模拟无边界）。
     * 返回接收方每次 read 到的原始字符串（很可能只有 1 条、3 条消息粘在一起）。
     */
    public static List<String> runTcp() throws IOException, InterruptedException {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        List<String> received = new ArrayList<>();

        Thread serverThread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                InputStream in = socket.getInputStream();
                byte[] buffer = new byte[1024];
                int count;
                // 循环 read：客户端 shutdownOutput 后 read 返回 -1
                while ((count = in.read(buffer)) != -1) {
                    received.add(new String(buffer, 0, count, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // 演示结束
            }
        });
        serverThread.start();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            // 应用层连续发送 3 条消息（以为发了 3 条独立消息）
            out.write("消息一|".getBytes(StandardCharsets.UTF_8));
            out.write("消息二|".getBytes(StandardCharsets.UTF_8));
            out.write("消息三|".getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput(); // 告诉对端写完了
        }
        serverThread.join();
        server.close();
        return received;
    }

    /** UDP：发送 3 个数据报，每个 receive 收到一个完整报文（有边界）。 */
    public static List<String> runUdp() throws Exception {
        List<String> received = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket(0)) {
            int port = socket.getLocalPort();
            Thread receiver = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        received.add(new String(packet.getData(), packet.getOffset(),
                                packet.getLength(), StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // 演示结束
                }
            });
            receiver.start();

            byte[] one = "消息一".getBytes(StandardCharsets.UTF_8);
            byte[] two = "消息二".getBytes(StandardCharsets.UTF_8);
            byte[] three = "消息三".getBytes(StandardCharsets.UTF_8);
            InetAddress target = InetAddress.getByName("127.0.0.1");
            socket.send(new DatagramPacket(one, one.length, target, port));
            socket.send(new DatagramPacket(two, two.length, target, port));
            socket.send(new DatagramPacket(three, three.length, target, port));
            receiver.join();
        }
        return received;
    }
}
