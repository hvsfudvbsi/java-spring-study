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
        // 1. UDP 没有 boss/worker 两阶段连接模型，一个 DatagramChannel 就可以收发数据报。
        EventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                // 2. NioDatagramChannel 对应底层 UDP Socket。
                .channel(NioDatagramChannel.class)
                // 3. 每个入站 DatagramPacket 直接交给数据报 Handler。
                .handler(new UdpServerHandler());
        // 4. bind 绑定本地 UDP 端口；sync 等待端口真正可用后再返回。
        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(future -> group.shutdownGracefully());
        return channel;
    }

    /** UDP 数据报处理器：DatagramPacket 自带发送方地址。 */
    public static class UdpServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            // 1. DatagramPacket 同时携带内容和地址；UDP 每个数据报都有独立边界。
            String message = packet.content().toString(CharsetUtil.UTF_8);
            String response = "UDP echo: " + message;
            // 2. 打印收到和回复的内容，便于观察真实链路。
            System.out.println("  [服务端] 收到数据报: " + message + "（来自 " + packet.sender() + "）");
            // 3. 回包目标必须使用 sender()，不能依赖 TCP 式的连接对象。
            ctx.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer(response, CharsetUtil.UTF_8), packet.sender()));
            System.out.println("  [服务端] 已回复: " + response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
