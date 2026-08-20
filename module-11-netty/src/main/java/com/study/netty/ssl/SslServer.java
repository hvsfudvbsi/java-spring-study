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
        SelfSignedCertificate certificate = new SelfSignedCertificate();
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
                        ch.pipeline()
                                .addLast(sslContext.newHandler(ch.alloc()))
                                .addLast(new LineBasedFrameDecoder(1024))
                                .addLast(new StringDecoder())
                                .addLast(new StringEncoder())
                                .addLast(new SslEchoHandler());
                    }
                });
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
            ctx.writeAndFlush("TLS echo: " + message + System.lineSeparator());
        }
    }
}
