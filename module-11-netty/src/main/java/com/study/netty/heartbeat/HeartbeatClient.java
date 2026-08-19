package com.study.netty.heartbeat;

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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * 实操示例二：心跳检测客户端
 *
 * 场景：客户端需要保持长连接，定期发送心跳告诉服务端"我还活着"。
 *
 * 实现：
 *   IdleStateHandler(0, 3, 0)：3 秒没写数据触发写空闲事件
 *   -> 写空闲时发送 PING，服务端回 PONG 证明链路正常
 *
 * 运行：
 *   1. 启动 HeartbeatServer（18081）
 *   2. 启动 HeartbeatClient，观察心跳交互日志
 *   3. 直接关掉客户端（强杀），观察服务端 5 秒后清理连接
 */
public class HeartbeatClient {

    private static final int WRITER_IDLE_SECONDS = 3;

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                // 3 秒没发送数据触发写空闲
                                new IdleStateHandler(0, WRITER_IDLE_SECONDS, 0, TimeUnit.SECONDS),
                                new HeartbeatClientHandler());
                    }
                });

        Channel channel = bootstrap.connect("127.0.0.1", HeartbeatServer.PORT).sync().channel();
        System.out.println("心跳客户端已连接 " + channel.remoteAddress() + "，每 3 秒发送一次 PING");

        // 运行 15 秒后主动关闭（演示结束）；实际长连接应用会一直运行
        Thread.sleep(15_000);
        channel.close().sync();
        group.shutdownGracefully();
        System.out.println("客户端已关闭");
    }

    static class HeartbeatClientHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("  [客户端] 连接建立: " + ctx.channel().remoteAddress());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            String data = buf.toString(CharsetUtil.UTF_8);
            if ("PONG".equals(data)) {
                System.out.println("  [客户端] 收到服务端心跳响应 PONG，链路正常");
            }
        }

        /** 写空闲触发：发送心跳 */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                System.out.println("  [客户端] 3 秒未发送数据，发送心跳 PING");
                ctx.writeAndFlush(Unpooled.copiedBuffer("PING", CharsetUtil.UTF_8));
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.out.println("  [客户端] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
