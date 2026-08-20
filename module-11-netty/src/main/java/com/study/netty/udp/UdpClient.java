package com.study.netty.udp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** 实操示例：发送 UDP 数据报并接收服务端响应。 */
public class UdpClient {

    public static void main(String[] args) throws Exception {
        System.out.println(send("127.0.0.1", UdpServer.DEFAULT_PORT, "你好，UDP"));
    }

    /** 发送一条 UDP 消息并返回响应正文。 */
    public static String send(String host, int port, String message) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new UdpClientHandler(responses));
                        }
                    });
            Channel channel = bootstrap.bind(0).sync().channel();
            InetSocketAddress server = new InetSocketAddress(host, port);
            channel.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer(message, CharsetUtil.UTF_8), server)).sync();
            String response = responses.poll(5, TimeUnit.SECONDS);
            channel.close().sync();
            return response;
        } finally {
            group.shutdownGracefully();
        }
    }

    private static class UdpClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final BlockingQueue<String> responses;

        private UdpClientHandler(BlockingQueue<String> responses) {
            this.responses = responses;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            responses.offer(packet.content().toString(CharsetUtil.UTF_8));
        }
    }
}
