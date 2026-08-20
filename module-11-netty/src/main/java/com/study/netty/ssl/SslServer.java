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
import io.netty.handler.ssl.util.SelfSignedCertificate;

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
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc()))
                                // 4. TLS 解密后仍是 TCP 字节流，所以先按行切帧。
                                .addLast(new LineBasedFrameDecoder(1024))
                                // 5. 把 UTF-8 字节转换为 Java String，再进入业务 Handler。
                                .addLast(new StringDecoder())
                                // 6. 业务返回的 String 经过 Encoder 和 SslHandler 后才写到网络。
                                .addLast(new StringEncoder())
                                .addLast(new SslEchoHandler());
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
