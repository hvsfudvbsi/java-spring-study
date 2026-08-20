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
        // 1. 创建客户端 TLS 配置。本示例信任所有证书，仅用于理解握手流程，不能用于生产。
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
                            // 2. 客户端 SslHandler 先完成 TLS 握手，并负责出入站加解密。
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, port))
                                    // 3. TLS 解密后按行切分，再转换为业务 String。
                                    .addLast(new LineBasedFrameDecoder(1024))
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    // 4. 最后把服务端回显放入队列，供调用线程读取。
                                    .addLast(new SslClientHandler(responses));
                        }
                    });
            // 5. connect 等待 TCP 连接建立；SslHandler 会在随后异步完成 TLS 握手。
            Channel channel = bootstrap.connect(host, port).sync().channel();
            // 6. 发送换行结尾的文本，匹配服务端 LineBasedFrameDecoder。
            channel.writeAndFlush(message + System.lineSeparator()).sync();
            // 7. Handler 收到回显后放入队列，超时返回 null 便于发现服务端没有响应。
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
