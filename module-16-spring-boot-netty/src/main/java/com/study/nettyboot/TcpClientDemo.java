package com.study.nettyboot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 手动验证用 TCP 客户端：连接 Netty 服务（默认 19090），
 * 读取欢迎消息，发送 3 行文本并打印回声。
 *
 * 运行：mvn compile exec:java -pl module-16-spring-boot-netty
 *   -Dexec.mainClass=com.study.nettyboot.TcpClientDemo
 * 或直接：java -cp ... com.study.nettyboot.TcpClientDemo [host] [port]
 */
public class TcpClientDemo {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 19090;

        try (Socket socket = new Socket(host, port)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            System.out.println("服务端: " + in.readLine()); // 欢迎消息
            for (int i = 1; i <= 3; i++) {
                out.write(("hello-" + i + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                System.out.println("发送 hello-" + i + " -> 回声: " + in.readLine());
            }
        }
    }
}
