package com.study.netty.echo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 实操示例一：回声客户端（Echo Client）
 *
 * 功能：连接 EchoServer，发送几条消息，打印服务端回声后退出。
 */
public class EchoClient {

    public static void main(String[] args) throws Exception {
        System.out.println("========== Echo 客户端 ==========");
        String response = run("127.0.0.1", EchoServer.DEFAULT_PORT, "你好，Netty!");
        System.out.println("  最后一条回声: " + response);
        System.exit(0); // 所有线程关闭后退出（EventLoop 非守护线程）
    }

    /** 连接服务器，发送消息，返回收到的回声 */
    public static String run(String host, int port, String message) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new EchoClientHandler(responses));
                        }
                    });

            Channel channel = bootstrap.connect(host, port).sync().channel();

            // 发送 3 条消息（服务端会依次回声）
            String lastEcho = null;
            for (int i = 1; i <= 3; i++) {
                channel.writeAndFlush(Unpooled.copiedBuffer(message + " (#" + i + ")", CharsetUtil.UTF_8))
                        .sync();
                lastEcho = responses.poll(5, TimeUnit.SECONDS);
                System.out.println("  [客户端] 收到回声: " + lastEcho);
            }

            channel.close().sync();
            return lastEcho; // 返回最后一条回声（循环里已消费，不能再 poll 空队列）
        } finally {
            group.shutdownGracefully();
        }
    }

    /** 业务处理器：把收到的回声放入队列 */
    static class EchoClientHandler extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<String> responses;

        EchoClientHandler(BlockingQueue<String> responses) {
            this.responses = responses;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf) {
                responses.offer(buf.toString(CharsetUtil.UTF_8));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.out.println("  [客户端] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
