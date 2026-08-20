package com.study.netty.performance;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * 性能对比用：Netty 回显服务（可配置 worker 线程数）。
 *
 * 与 BlockingEchoServer 提供相同的按行回显协议，但 IO 线程固定为
 * boss(1) + worker(可配置) 少量线程，靠 NIO 多路复用处理所有连接。
 */
public class NettyEchoServer {

    /** 启动 Netty 回显服务；关闭返回的 Channel 后线程组自动优雅关闭。 */
    public static Channel start(int port, int workerThreads) throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup(workerThreads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 4096)       // 高并发连接演示需要较大等待队列
                .childOption(ChannelOption.TCP_NODELAY, true) // 压测回环下关闭 Nagle，降低延迟
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new LineBasedFrameDecoder(64 * 1024),
                                new StringDecoder(CharsetUtil.UTF_8),
                                new StringEncoder(CharsetUtil.UTF_8),
                                new SimpleChannelInboundHandler<String>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                        // 原样回写（带换行符，与 BlockingEchoServer 协议一致）
                                        ctx.writeAndFlush(msg + System.lineSeparator());
                                    }
                                });
                    }
                });

        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        return channel;
    }
}
