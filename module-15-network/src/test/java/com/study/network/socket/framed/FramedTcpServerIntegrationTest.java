package com.study.network.socket.framed;

import com.study.network.socket.framed.FrameCodec.FrameDecoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帧协议多线程服务器集成测试：真实回环连接，验证帧边界在真实网络上也不丢失。
 */
class FramedTcpServerIntegrationTest {

    private FramedTcpServerFixture server;

    @BeforeEach
    void startServer() throws IOException {
        server = new FramedTcpServerFixture();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
    }

    @Test
    @DisplayName("真实回环：发送多帧，逐帧收到回声（帧边界不丢失）")
    void echoFramesOverRealSocket() throws Exception {
        List<String> messages = List.of(
                "你好", "world", "中文消息，长度头按字节", "最后一帧");
        List<String> responses = sendAndReceive(messages);

        assertEquals(messages.size(), responses.size(), "每帧都应收到对应回声");
        for (int i = 0; i < messages.size(); i++) {
            assertEquals("echo: " + messages.get(i), responses.get(i));
        }
    }

    @Test
    @DisplayName("真实回环：含换行/特殊字符的内容也能完整传输（帧协议不受分隔符限制）")
    void echoFramesWithSpecialCharacters() throws Exception {
        List<String> messages = List.of(
                "含换行\n的内容",
                "含中文：网络编程",
                "含制表符\t和空格  ",
                "emoji 🚀 测试");
        List<String> responses = sendAndReceive(messages);

        for (int i = 0; i < messages.size(); i++) {
            assertEquals("echo: " + messages.get(i), responses.get(i));
        }
    }

    @Test
    @DisplayName("真实回环：两个客户端并发连接，各自帧互不混淆")
    void twoClientsConcurrently() throws Exception {
        List<String> clientAMessages = List.of("A1", "A2", "A3");
        List<String> clientBMessages = List.of("B1", "B2", "B3");

        Thread threadA = new Thread(() -> {
            try {
                List<String> responses = sendAndReceive(clientAMessages);
                assertEquals("echo: A3", responses.get(2));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread threadB = new Thread(() -> {
            try {
                List<String> responses = sendAndReceive(clientBMessages);
                assertEquals("echo: B3", responses.get(2));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        threadA.start();
        threadB.start();
        threadA.join(5000);
        threadB.join(5000);
        assertTrue(!threadA.isAlive() && !threadB.isAlive(), "两个客户端都应正常完成");
    }

    @Test
    @DisplayName("真实回环：一帧内容跨多个 TCP 分段到达时，服务端能拼回完整帧")
    void frameSplitAcrossTcpSegments() throws Exception {
        // 长内容 + 客户端分多次写：每次写一小段，模拟真实网络拆包
        String longMessage = "很长的消息".repeat(200);
        List<String> responses = sendInChunks(longMessage, 50);

        assertEquals(1, responses.size());
        assertEquals("echo: " + longMessage, responses.get(0));
    }

    /** 发送多帧并收集全部回声 */
    private List<String> sendAndReceive(List<String> messages) throws IOException {
        return sendChunked(messages, Integer.MAX_VALUE);
    }

    /** 把每条消息按固定小块写出（模拟 TCP 分段/拆包） */
    private List<String> sendInChunks(String message, int chunkSize) throws IOException {
        return sendChunked(List.of(message), chunkSize);
    }

    private List<String> sendChunked(List<String> messages, int chunkSize) throws IOException {
        List<String> responses = new ArrayList<>();
        try (Socket socket = new Socket("127.0.0.1", server.port());
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            for (String message : messages) {
                byte[] frame = FrameCodec.encode(message);
                // 按 chunkSize 分块写出（模拟拆包）；chunkSize 很大时一次写完
                for (int offset = 0; offset < frame.length; offset += Math.min(chunkSize, frame.length - offset)) {
                    int len = Math.min(chunkSize, frame.length - offset);
                    out.write(frame, offset, len);
                }
            }
            out.flush();
            socket.shutdownOutput();

            // 接收：按帧解码回声
            FrameDecoder decoder = new FrameDecoder();
            byte[] buffer = new byte[4096];
            int count;
            while (responses.size() < messages.size() && (count = in.read(buffer)) != -1) {
                byte[] chunk = new byte[count];
                System.arraycopy(buffer, 0, chunk, 0, count);
                responses.addAll(decoder.decode(chunk));
            }
        }
        return responses;
    }

    /** 可复用的测试服务器：线程每连接一处理，与生产实现一致 */
    private static class FramedTcpServerFixture {

        private ServerSocket serverSocket;
        private final List<Thread> workers = new ArrayList<>();
        private volatile boolean running = true;

        void start() throws IOException {
            serverSocket = new ServerSocket(0);
            Thread acceptor = new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        Thread worker = new Thread(() -> handle(socket));
                        worker.start();
                        workers.add(worker);
                    } catch (IOException e) {
                        // 服务器关闭
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void handle(Socket socket) {
            try (socket;
                 InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                FrameDecoder decoder = new FrameDecoder();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    byte[] chunk = new byte[count];
                    System.arraycopy(buffer, 0, chunk, 0, count);
                    for (String frame : decoder.decode(chunk)) {
                        out.write(FrameCodec.encode("echo: " + frame));
                        out.flush();
                    }
                }
            } catch (IOException e) {
                // 客户端断开
            }
        }

        void close() throws IOException {
            running = false;
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }
}
