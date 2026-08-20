package com.study.netty.udp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

/** 实操示例：Netty 无连接 UDP 服务，收到数据报后回传响应。 */
public class UdpServer {

    public static final int DEFAULT_PORT = 18084;

    public static void main(String[] args) throws Exception {
        Channel channel = start(DEFAULT_PORT);
        System.out.println("Netty UDP 服务器已启动: " + channel.localAddress());
        channel.closeFuture().sync();
    }

    /** 启动 UDP 服务。 */
    public static Channel start(int port) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new UdpServerHandler());
        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(future -> group.shutdownGracefully());
        return channel;
    }

    /** UDP 数据报处理器：DatagramPacket 自带发送方地址。 */
    public static class UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            String message = packet.content().toString(CharsetUtil.UTF_8);
            String response = "UDP echo: " + message;
            ctx.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer(response, CharsetUtil.UTF_8), packet.sender()));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
