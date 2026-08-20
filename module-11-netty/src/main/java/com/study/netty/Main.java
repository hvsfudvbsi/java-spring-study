package com.study.netty;

/**
 * Netty 学习模块总入口
 *
 * 运行方式（IDEA 中右键 Run，或命令行）：
 *   mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.Main
 *
 * 本模块分两部分：
 *   1. API 方法用例（无需网络）：运行所有 apidemo 和 codec 演示
 *   2. 网络实操示例（需分别启动）：TCP Echo / Heartbeat / IM、HTTP、UDP、WebSocket、TLS/SSL
 *
 * 注意：Main 只运行不会占用端口的 API 和编解码演示；网络服务必须按 README
 * 的顺序分别启动服务端和客户端，这样可以观察真实连接、事件循环和协议握手。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  第一部分：API 方法用例（常用+不常用）");
        System.out.println("========================================");

        com.study.netty.apidemo.ByteBufApiDemo.main(args);
        System.out.println();

        com.study.netty.apidemo.EventLoopApiDemo.main(args);
        System.out.println();

        com.study.netty.apidemo.FutureApiDemo.main(args);
        System.out.println();

        com.study.netty.apidemo.PipelineApiDemo.main(args);
        System.out.println();

        com.study.netty.apidemo.ChannelApiDemo.main(args);
        System.out.println();

        com.study.netty.apidemo.BootstrapApiDemo.main(args);
        System.out.println();

        com.study.netty.codec.FrameDecoderDemo.main(args);
        System.out.println();

        com.study.netty.codec.CustomCodecDemo.main(args);
        System.out.println();

        System.out.println("========================================");
        System.out.println("  第二部分：网络实操示例（需分别启动）");
        System.out.println("========================================");
        System.out.println("  1. TCP 回声 : com.study.netty.echo.EchoServer -> EchoClient");
        System.out.println("  2. TCP 心跳 : com.study.netty.heartbeat.HeartbeatServer -> HeartbeatClient");
        System.out.println("  3. TCP IM   : com.study.netty.chat.ChatServer -> 多个 ChatClient");
        System.out.println("  4. HTTP     : com.study.netty.http.HttpServer -> HttpClient/curl");
        System.out.println("  5. UDP      : com.study.netty.udp.UdpServer -> UdpClient");
        System.out.println("  6. WebSocket: com.study.netty.websocket.WebSocketServer -> 浏览器");
        System.out.println("  7. TLS/SSL  : com.study.netty.ssl.SslServer -> SslClient");
        System.out.println("  详情见 module-11-netty/README.md 的完整协议说明和运行步骤。");
    }
}
