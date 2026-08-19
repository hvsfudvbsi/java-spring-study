package com.study.netty.chat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * 群聊核心业务处理器
 *
 * 关键设计：
 *   - ChannelGroup：管理所有在线连接，broadcast() 一键广播
 *   - AttributeKey：给每个 Channel 附加昵称属性（channel.attr(key).set(...)）
 *   - SimpleChannelInboundHandler<String>：自动释放消息 + 泛型直接拿到 String
 */
public class ChatServerHandler extends SimpleChannelInboundHandler<String> {

    /** 所有在线连接（全局唯一） */
    private static final ChannelGroup CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /** 昵称属性 key：挂在每个 Channel 上 */
    private static final AttributeKey<String> NICKNAME = AttributeKey.valueOf("nickname");

    /** 新连接加入：欢迎 + 广播上线通知 */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        // 默认昵称：用连接地址后 4 位，方便区分
        String defaultNick = "用户-" + channel.remoteAddress().toString().replaceAll("\\D", "").substring(0, 4);
        channel.attr(NICKNAME).set(defaultNick);
        CHANNELS.add(channel); // 加入群组

        channel.writeAndFlush("[系统] 欢迎 " + defaultNick + "！发送 'NICK:新昵称' 改名，发送 'quit' 退出");
        broadcast("[系统] " + defaultNick + " 加入了聊天室（当前在线 " + CHANNELS.size() + " 人）", channel);
        System.out.println("[上线] " + defaultNick + " 连接: " + channel.remoteAddress());
    }

    /** 收到消息：NICK:xxx 改名，其余内容群发 */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        Channel channel = ctx.channel();
        String nick = channel.attr(NICKNAME).get();

        if (msg.startsWith("NICK:")) {
            // 设置昵称
            String newNick = msg.substring(5).trim();
            channel.attr(NICKNAME).set(newNick);
            channel.writeAndFlush("[系统] 昵称已改为: " + newNick);
            broadcast("[系统] " + nick + " 改名为 " + newNick, channel);
            return;
        }

        if ("quit".equalsIgnoreCase(msg.trim())) {
            channel.writeAndFlush("[系统] 再见 " + nick + "！");
            ctx.close(); // 触发 channelInactive
            return;
        }

        // 群发消息（格式: 昵称: 内容）
        System.out.println("[消息] " + nick + ": " + msg);
        broadcast(nick + ": " + msg, channel);
    }

    /** 连接断开：移出群组 + 广播离线通知 */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String nick = channel.attr(NICKNAME).get();
        CHANNELS.remove(channel);
        broadcast("[系统] " + nick + " 离开了聊天室（当前在线 " + CHANNELS.size() + " 人）", channel);
        System.out.println("[下线] " + nick + " 断开连接");
    }

    /** 广播消息：发给除发送者外的所有人 */
    private void broadcast(String message, Channel sender) {
        for (Channel ch : CHANNELS) {
            if (ch != sender) {
                ch.writeAndFlush(message);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[异常] " + cause.getMessage());
        ctx.close();
    }
}
