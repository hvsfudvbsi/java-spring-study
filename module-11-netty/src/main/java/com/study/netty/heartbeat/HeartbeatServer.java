package com.study.netty.heartbeat;

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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * 实操示例二：心跳检测服务器
 *
 * 场景：客户端异常断开（断网、崩溃）时，服务端不知道连接已死，
 * 会一直持有资源。心跳机制让服务端主动清理"假死"连接。
 *
 * 实现：IdleStateHandler（Netty 内置空闲检测）
 *   IdleStateHandler(readerIdleTime, writerIdleTime, allIdleTime, unit)
 *   - readerIdleTime：读超时（多久没收到数据触发）
 *   - writerIdleTime：写超时（多久没发送数据触发）
 *   - allIdleTime   ：读写都超时触发
 *
 * 触发后产生 IdleStateEvent 事件，通过 userEventTriggered 接收。
 * 本示例：5 秒没收到客户端任何数据 -> 判定假死 -> 关闭连接。
 */
public class HeartbeatServer {

    public static final int PORT = 18081;
    /** 读空闲阈值：5 秒没收到数据就断开 */
    private static final int READER_IDLE_SECONDS = 5;

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                // 1. 空闲检测器：5 秒没读、10 秒没写、0 不检测全部空闲
                                new IdleStateHandler(READER_IDLE_SECONDS, 10, 0, TimeUnit.SECONDS),
                                // 2. 业务处理器
                                new HeartbeatServerHandler());
                    }
                });

        Channel serverChannel = bootstrap.bind(PORT).sync().channel();
        System.out.println("心跳服务器已启动: " + serverChannel.localAddress());
        System.out.println("客户端 5 秒不发送数据将被判定假死并断开");
        serverChannel.closeFuture().sync();

        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }

    static class HeartbeatServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            String data = buf.toString(CharsetUtil.UTF_8);
            System.out.println("  [服务端] 收到: " + data + "（来自 " + ctx.channel().remoteAddress() + "）");

            if ("PING".equals(data)) {
                // 收到客户端心跳，回 PONG
                ctx.writeAndFlush(io.netty.buffer.Unpooled.copiedBuffer("PONG", CharsetUtil.UTF_8));
            }
        }

        /** 空闲事件处理：读超时 -> 判定假死 -> 关闭连接 */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent event) {
                System.out.println("  [服务端] 读空闲超时，判定连接假死，关闭: "
                        + ctx.channel().remoteAddress()
                        + "（事件类型: " + event.state() + "）");
                ctx.close();
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
