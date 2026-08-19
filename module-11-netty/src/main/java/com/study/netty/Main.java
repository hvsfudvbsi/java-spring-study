package com.study.netty;

/**
 * Netty 学习模块总入口
 *
 * 运行方式（IDEA 中右键 Run，或命令行）：
 *   mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.Main
 *
 * 本模块分两部分：
 *   1. API 方法用例（无需网络）：运行所有 apidemo 和 codec 演示
 *   2. 网络实操示例（需分别启动）：echo / heartbeat / chat
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
        System.out.println("  1. 回声  : com.study.netty.echo.EchoServer  ->  EchoClient");
        System.out.println("  2. 心跳  : com.study.netty.heartbeat.HeartbeatServer -> HeartbeatClient");
        System.out.println("  3. 群聊  : com.study.netty.chat.ChatServer   ->  多个 ChatClient");
        System.out.println("  详情见 module-11-netty/README.md");
    }
}
