package com.study.netty.ssl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLSession;
import java.net.ServerSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 实操示例：亲眼观察一次 TLS 握手（详细步骤演示）
 *
 * 功能：在同一个进程内启动 TLS 服务端和客户端完成一次真实握手，
 *   - 开启 JSSE 握手跟踪（javax.net.debug=ssl:handshake），打印 ClientHello → ServerHello
 *     → EncryptedExtensions → Certificate → CertificateVerify → Finished 每一步的真实报文；
 *   - 打印 TLS 1.3 握手步骤注解，便于对照输出逐条理解；
 *   - 握手完成后打印协商结果（协议版本、密码套件）并完成一次回显。
 *
 * 运行：
 *   mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.ssl.SslHandshakeDemo
 */
public class SslHandshakeDemo {

    public static void main(String[] args) throws Exception {
        // 必须在任何 JSSE 类初始化之前设置，才能输出每个握手报文的详细跟踪。
        // 想看密钥/随机数等更多细节可改为 "ssl:handshake:verbose"。
        System.setProperty("javax.net.debug", "ssl:handshake");
        printHandshakeGuide();
        System.out.println("======== 开始真实 TLS 握手（注意上方 ssl 开头的握手跟踪日志） ========\n");
        String summary = runDemo();
        System.out.println("\n======== 握手演示完成 ========");
        System.out.println(summary);
        System.exit(0); // 结束后退出，避免 EventLoop 非守护线程阻止 JVM 退出
    }

    /**
     * 运行一次真实 TLS 握手（服务端 + 客户端同进程），返回协商结果与回显摘要。
     * 供 main 和测试复用；测试调用时不开启 javax.net.debug，避免污染其他测试输出。
     */
    public static String runDemo() throws Exception {
        // 1. 找一个空闲端口启动 TLS 服务端（复用 SslServer，自签名证书 + 行帧 + 回显）。
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Channel server = SslServer.start(port);
        try {
            // 2. 客户端 TLS 配置：信任所有证书（学习用途）。
            SslContext clientContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            EventLoopGroup group = new NioEventLoopGroup(1);
            BlockingQueue<String> sessionInfo = new LinkedBlockingQueue<>();
            BlockingQueue<String> echoes = new LinkedBlockingQueue<>();
            try {
                // 3. 客户端 Pipeline：SslHandler 先做握手与加解密，之后按行解码业务文本。
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                SslHandler sslHandler = clientContext.newHandler(ch.alloc(), "127.0.0.1", port);
                                ch.pipeline().addLast(sslHandler)
                                        .addLast(new LineBasedFrameDecoder(1024))
                                        .addLast(new StringDecoder())
                                        .addLast(new StringEncoder())
                                        .addLast(new SimpleChannelInboundHandler<String>() {
                                            @Override
                                            protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                                echoes.offer(msg);
                                            }
                                        });
                                // 4. 握手完成事件：记录协商出的协议版本和密码套件。
                                sslHandler.handshakeFuture().addListener(future -> {
                                    if (future.isSuccess()) {
                                        SSLSession session = sslHandler.engine().getSession();
                                        sessionInfo.offer("协议=" + session.getProtocol()
                                                + ", 密码套件=" + session.getCipherSuite());
                                    } else {
                                        sessionInfo.offer("握手失败: " + future.cause());
                                    }
                                });
                            }
                        });

                Channel channel = bootstrap.connect("127.0.0.1", port).sync().channel();
                // 5. 发送一行文本（带换行符，与服务端 LineBasedFrameDecoder 配套）。
                channel.writeAndFlush("handshake-demo" + System.lineSeparator()).sync();
                String echo = echoes.poll(8, TimeUnit.SECONDS);
                String info = sessionInfo.poll(8, TimeUnit.SECONDS);
                channel.close().sync();
                return "协商结果: " + info + "\n回显: " + echo;
            } finally {
                group.shutdownGracefully();
            }
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    /** 打印 TLS 1.3 握手步骤注解，与 javax.net.debug 输出逐条对应。 */
    private static void printHandshakeGuide() {
        System.out.println("======== TLS 1.3 握手步骤（下面将打印每一步的真实报文） ========");
        System.out.println("  1. ClientHello          客户端 → 服务器：客户端随机数、支持的 TLS 版本/密码套件、SNI");
        System.out.println("  2. ServerHello          服务器 → 客户端：选定版本/密码套件、服务器随机数");
        System.out.println("  3. EncryptedExtensions  服务器 → 客户端：扩展信息（此后流量加密）");
        System.out.println("  4. Certificate          服务器 → 客户端：服务器证书链（本示例为自签名）");
        System.out.println("  5. CertificateVerify    服务器 → 客户端：私钥签名，证明证书与私钥匹配");
        System.out.println("  6. Finished             服务器 → 客户端：握手消息完整性校验值");
        System.out.println("  7. Finished             客户端 → 服务器：客户端同样发送校验值");
        System.out.println("  8. Application Data     双向：此后业务数据全部加密传输");
        System.out.println();
    }
}
