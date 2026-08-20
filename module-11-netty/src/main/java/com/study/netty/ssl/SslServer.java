package com.study.netty.ssl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;

/** 实操示例：TLS 加密的 Netty 文本回声服务。 */
public class SslServer {

    public static final int DEFAULT_PORT = 18086;

    public static void main(String[] args) throws Exception {
        Channel channel = start(DEFAULT_PORT);
        System.out.println("Netty TLS 服务器已启动: " + channel.localAddress());
        channel.closeFuture().sync();
    }

    /** 使用临时自签名证书启动 TLS 服务，仅用于学习和本地测试。 */
    public static Channel start(int port) throws Exception {
        // 1. 生成临时自签名证书；只用于本地学习，生产环境必须使用正式证书和私钥管理方案。
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        // 2. SslContext 保存 TLS 配置，服务端使用证书链和私钥完成握手、加密和解密。
        SslContext sslContext = SslContextBuilder
                .forServer(certificate.certificate(), certificate.privateKey())
                .build();
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 3. SslHandler 必须位于业务协议前：先解密入站字节，先加密出站字节。
                        SslHandler sslHandler = sslContext.newHandler(ch.alloc());
                        ch.pipeline().addLast(sslHandler)
                                // 4. TLS 解密后仍是 TCP 字节流，所以先按行切帧。
                                .addLast(new LineBasedFrameDecoder(1024))
                                // 5. 把 UTF-8 字节转换为 Java String，再进入业务 Handler。
                                .addLast(new StringDecoder())
                                // 6. 业务返回的 String 经过 Encoder 和 SslHandler 后才写到网络。
                                .addLast(new StringEncoder())
                                .addLast(new SslEchoHandler());
                        // 7. 观察握手完成事件：打印协商出的协议版本、密码套件和对端证书。
                        sslHandler.handshakeFuture().addListener(future -> {
                            if (future.isSuccess()) {
                                SSLSession session = sslHandler.engine().getSession();
                                System.out.println("  [服务端] TLS 握手成功: 协议=" + session.getProtocol()
                                        + ", 密码套件=" + session.getCipherSuite()
                                        + ", 对端证书=" + peerSubject(session));
                            } else {
                                System.out.println("  [服务端] TLS 握手失败: " + future.cause().getMessage());
                            }
                        });
                    }
                });
        // 7. bind 成功后服务端开始监听；关闭服务端时释放 TLS 所在的 EventLoop 线程组。
        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        return channel;
    }

    /** 读取会话中的对端证书主体；客户端未提供证书（无客户端认证）时返回说明。 */
    private static String peerSubject(SSLSession session) {
        try {
            java.security.cert.Certificate[] certs = session.getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                return x509.getSubjectX500Principal().getName();
            }
            return "无";
        } catch (Exception e) {
            return "未提供（无客户端认证）";
        }
    }

    private static class SslEchoHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String message) {
            // 打印收到和回复的内容，便于观察真实链路（与服务端其他示例保持一致）。
            System.out.println("  [服务端] 收到: " + message + "（来自 " + ctx.channel().remoteAddress() + "）");
            ctx.writeAndFlush("TLS echo: " + message + System.lineSeparator());
            System.out.println("  [服务端] 已回复: TLS echo: " + message);
        }
    }
}
