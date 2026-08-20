package com.study.netty.chat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * 实操示例三：群聊服务器（完整项目）
 *
 * 功能：
 *   - 多个客户端可同时连接，任意一人发言全员可见
 *   - 新用户加入/退出时广播通知
 *   - 支持设置昵称（第一条消息格式: NICK:昵称）
 *   - 支持私聊：@昵称 内容 只发给指定用户（不广播）
 *
 * 技术点：
 *   - StringDecoder/StringEncoder：字符串编解码（简化消息处理）
 *   - ChannelGroup：Netty 内置的 Channel 集合，一键群发（自动排除自己）
 *   - ChannelOption.SO_BACKLOG：服务端等待队列
 *   - AttributeKey：昵称挂在 Channel 属性上，私聊时遍历匹配
 *
 * 协议（字符串，按行）：
 *   NICK:小明        -> 设置昵称
 *   @小明 你好       -> 私聊（只发给小明，其他人看不到）
 *   其他任意内容      -> 群聊消息
 *   quit             -> 退出聊天
 *
 * 运行：
 *   1. 启动 ChatServer（18082）
 *   2. 启动多个 ChatClient（IDEA 中可开多个实例）即可群聊
 */
public class ChatServer {

    public static final int PORT = 18082;

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)      // 等待队列长度
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // TCP 保活
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    // TCP 是字节流，必须先按客户端发送的换行符恢复消息边界。
                                    new LineBasedFrameDecoder(64 * 1024),
                                    new StringDecoder(CharsetUtil.UTF_8), // 入站：按行字节 -> 字符串
                                    new StringEncoder(CharsetUtil.UTF_8), // 出站：字符串 -> 字节
                                    new ChatLineEncoder(),                 // 出站：每条消息补换行符，客户端才能按行还原
                                    new ChatServerHandler());              // 业务处理
                        }
                    });

            Channel serverChannel = bootstrap.bind(PORT).sync().channel();
            System.out.println("========== 群聊服务器启动 ==========");
            System.out.println("监听端口: " + PORT);
            System.out.println("连接协议: 首条消息 'NICK:昵称' 设置昵称，'@昵称 内容' 私聊，之后直接发消息群聊");
            serverChannel.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
