package com.study.nettyboot;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

/**
 * Netty TCP 业务处理器：按行协议，收到消息回显 "echo: xxx"。
 *
 * 关键点：这个 Handler 是 Spring 管理的单例（@Component + @Sharable），
 * 所有连接共用同一个实例，因此不能保存"每连接"状态；跨连接共享的状态
 * （在线数、消息总数）放在线程安全的 TcpStats Bean 里。
 */
@Component
@ChannelHandler.Sharable
public class NettyTcpServerHandler extends SimpleChannelInboundHandler<String> {

    private final TcpStats stats;

    public NettyTcpServerHandler(TcpStats stats) {
        this.stats = stats;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        stats.onConnect();
        ctx.writeAndFlush("welcome, 当前在线 " + stats.activeConnections() + "\n");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        stats.onMessage();
        ctx.writeAndFlush("echo: " + msg + "\n");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        stats.onDisconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[Netty] 连接异常: " + cause.getMessage());
        ctx.close();
    }
}
