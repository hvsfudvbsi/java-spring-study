package com.study.netty.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * 实操示例一：回声服务器（Echo Server）
 *
 * 功能：收到什么就回什么，是网络编程的 "Hello World"。
 *
 * 运行：
 *   1. 启动 EchoServer（监听 18080）
 *   2. 启动 EchoClient（连接并发送消息）
 *
 * 启动方式：IDEA 直接运行 main，或命令行：
 *   mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.echo.EchoServer
 */
public class EchoServer {

    public static final int DEFAULT_PORT = 18080;

    public static void main(String[] args) throws Exception {
        Channel serverChannel = start(DEFAULT_PORT);
        System.out.println("回声服务器已启动: " + serverChannel.localAddress());
        System.out.println("按 Ctrl+C 退出");
        // 等待服务端通道关闭（阻塞主线程，保持进程存活）
        serverChannel.closeFuture().sync();
    }

    /** 启动服务器（供 main 和测试复用）。关闭返回的 Channel 后线程组自动优雅关闭 */
    public static Channel start(int port) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new EchoServerHandler());
                    }
                });

        Channel serverChannel = bootstrap.bind(port).sync().channel();
        // 服务端关闭后自动关闭线程组（否则非守护线程会阻止 JVM 退出）
        serverChannel.closeFuture().addListener(f -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        return serverChannel;
    }

    /** 业务处理器：收到字节 -> 原样写回 */
    static class EchoServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            System.out.println("  [服务端] 收到 " + buf.readableBytes() + " 字节: "
                    + buf.toString(io.netty.util.CharsetUtil.UTF_8));
            // 直接把收到的消息写回（msg 会沿出站方向传递，由 Netty 释放）
            ctx.writeAndFlush(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.out.println("  [服务端] 连接异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
