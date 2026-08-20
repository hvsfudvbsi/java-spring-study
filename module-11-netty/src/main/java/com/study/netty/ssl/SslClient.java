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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** 实操示例：连接本地 TLS 服务并发送文本。仅信任所有证书用于学习。 */
public class SslClient {

    public static void main(String[] args) throws Exception {
        System.out.println(connect("127.0.0.1", SslServer.DEFAULT_PORT, "你好，TLS"));
    }

    /** 连接 TLS 服务、发送一行文本并返回响应。 */
    public static String connect(String host, int port, String message) throws Exception {
        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        EventLoopGroup group = new NioEventLoopGroup(1);
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(sslContext.newHandler(ch.alloc(), host, port))
                                    .addLast(new LineBasedFrameDecoder(1024))
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    .addLast(new SslClientHandler(responses));
                        }
                    });
            Channel channel = bootstrap.connect(host, port).sync().channel();
            channel.writeAndFlush(message + System.lineSeparator()).sync();
            String response = responses.poll(5, TimeUnit.SECONDS);
            channel.close().sync();
            return response;
        } finally {
            group.shutdownGracefully();
        }
    }

    private static class SslClientHandler extends SimpleChannelInboundHandler<String> {
        private final BlockingQueue<String> responses;

        private SslClientHandler(BlockingQueue<String> responses) {
            this.responses = responses;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String message) {
            responses.offer(message);
        }
    }
}
