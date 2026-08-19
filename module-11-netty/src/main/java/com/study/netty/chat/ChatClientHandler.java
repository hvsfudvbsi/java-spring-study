package com.study.netty.chat;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 群聊客户端处理器：打印服务器推送的所有消息
 * （在 Netty 的 EventLoop 线程中执行，与主线程的控制台输入互不干扰）
 */
public class ChatClientHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 打印收到的群聊消息（服务器推送）
        System.out.println(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("连接异常: " + cause.getMessage());
        ctx.close();
    }
}
