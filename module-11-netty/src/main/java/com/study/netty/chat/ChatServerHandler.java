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

    /** 所有在线连接（默认全局共享；测试时可注入独立群组） */
    private static final ChannelGroup DEFAULT_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ChannelGroup channels;

    public ChatServerHandler() {
        this(DEFAULT_CHANNELS);
    }

    public ChatServerHandler(ChannelGroup channels) {
        this.channels = channels;
    }

    /** 昵称属性 key：挂在每个 Channel 上 */
    private static final AttributeKey<String> NICKNAME = AttributeKey.valueOf("nickname");

    /** 新连接加入：欢迎 + 广播上线通知 */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        // 默认昵称：用连接地址后 4 位，方便区分
        String digits = String.valueOf(channel.remoteAddress()).replaceAll("\\D", "");
        String suffix = digits.length() >= 4 ? digits.substring(0, 4) : "未知";
        String defaultNick = "用户-" + suffix;
        channel.attr(NICKNAME).set(defaultNick);
        channels.add(channel); // 加入群组

        channel.writeAndFlush("[系统] 欢迎 " + defaultNick + "！发送 'NICK:新昵称' 改名，发送 'quit' 退出");
        broadcast("[系统] " + defaultNick + " 加入了聊天室（当前在线 " + channels.size() + " 人）", channel);
        System.out.println("[上线] " + defaultNick + " 连接: " + channel.remoteAddress());
    }

    /** 收到消息：NICK:xxx 改名、@昵称 私聊、quit 退出，其余内容群发 */
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

        if (msg.startsWith("@")) {
            // 私聊：@昵称 内容，只发给指定用户（不广播）
            sendPrivateMessage(channel, nick, msg);
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

    /** 私聊：解析 @昵称 内容，只发给昵称匹配的那一个用户 */
    private void sendPrivateMessage(Channel sender, String senderNick, String raw) {
        int spaceIndex = raw.indexOf(' ');
        if (spaceIndex <= 1) {
            // 没有 '@昵称 内容' 的结构（如单独一个 @ 或 @昵称 无内容）
            sender.writeAndFlush("[系统] 私聊格式: @昵称 内容，例如 '@Alice 你好'");
            return;
        }
        String targetNick = raw.substring(1, spaceIndex).trim();
        String content = raw.substring(spaceIndex + 1).trim();
        if (targetNick.isEmpty() || content.isEmpty()) {
            sender.writeAndFlush("[系统] 私聊格式: @昵称 内容，例如 '@Alice 你好'");
            return;
        }

        // 遍历在线用户，匹配昵称属性（AttributeKey）
        for (Channel ch : channels) {
            if (targetNick.equals(ch.attr(NICKNAME).get())) {
                ch.writeAndFlush("[私聊] " + senderNick + ": " + content);
                if (ch != sender) {
                    // 给发送者回执，确认已送达
                    sender.writeAndFlush("[私聊→" + targetNick + "] 已送达");
                }
                return;
            }
        }
        // 目标不在线
        sender.writeAndFlush("[系统] 用户 " + targetNick + " 不在线");
    }

    /** 连接断开：移出群组 + 广播离线通知 */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String nick = channel.attr(NICKNAME).get();
        channels.remove(channel);
        broadcast("[系统] " + nick + " 离开了聊天室（当前在线 " + channels.size() + " 人）", channel);
        System.out.println("[下线] " + nick + " 断开连接");
    }

    /** 广播消息：发给除发送者外的所有人 */
    private void broadcast(String message, Channel sender) {
        for (Channel ch : channels) {
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
