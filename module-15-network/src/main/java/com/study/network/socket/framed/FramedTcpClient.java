package com.study.network.socket.framed;

import com.study.network.socket.framed.FrameCodec.FrameDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * 带长度头帧协议的客户端：连续发送 N 帧，按帧接收回声。
 *
 * 关键点：
 * - 发送方用 FrameCodec.encode 给每条消息加长度头
 * - 接收方用 FrameDecoder 按长度头拆帧，不会因为粘包而混淆两条消息
 * - 对比 TcpEchoClient（readLine 行协议）：本客户端可发送任意内容（含换行）
 */
public class FramedTcpClient {

    public static void main(String[] args) throws Exception {
        List<String> responses = sendFrames("127.0.0.1", FramedTcpServer.DEFAULT_PORT,
                List.of("第一条消息", "第二条消息，含换行\n也不怕", "第三条消息"));
        System.out.println("收到 " + responses.size() + " 条回声:");
        responses.forEach(r -> System.out.println("  -> " + r));
    }

    /** 发送多帧并逐帧收集回声 */
    public static List<String> sendFrames(String host, int port, List<String> messages)
            throws IOException {
        List<String> responses = new ArrayList<>();
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {
            // 1. 连续发送多帧（每帧自带长度头）
            for (String message : messages) {
                out.write(FrameCodec.encode(message));
            }
            out.flush();
            socket.shutdownOutput(); // 写完告诉服务端（服务端 read 返回 -1 后关闭）

            // 2. 按帧接收回声：期望收到与发送数量相同的完整帧
            FrameDecoder decoder = new FrameDecoder();
            byte[] buffer = new byte[1024];
            int count;
            while (responses.size() < messages.size() && (count = in.read(buffer)) != -1) {
                responses.addAll(decoder.decode(java.util.Arrays.copyOf(buffer, count)));
            }
        }
        return responses;
    }
}
