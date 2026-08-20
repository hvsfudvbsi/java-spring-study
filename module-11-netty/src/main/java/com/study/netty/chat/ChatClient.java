package com.study.netty.chat;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 实操示例三：群聊客户端（完整项目）
 *
 * 运行：
 *   1. 先启动 ChatServer
 *   2. 启动多个 ChatClient 实例（IDEA: 右上角 Run -> Edit Configurations 可开多个）
 *   3. 输入 'NICK:小明' 设置昵称，输入 '@昵称 内容' 私聊，直接输入内容群聊
 *
 * 多线程模型：
 *   - 主线程：读取控制台输入并发送
 *   - Netty EventLoop 线程：接收服务器推送并打印（见 ChatClientHandler）
 */
public class ChatClient {

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new StringDecoder(CharsetUtil.UTF_8),
                                    new StringEncoder(CharsetUtil.UTF_8),
                                    new ChatClientHandler());
                        }
                    });

            Channel channel = bootstrap.connect("127.0.0.1", ChatServer.PORT).sync().channel();
            System.out.println("已连接到群聊服务器，输入 'NICK:昵称' 设置昵称，'@昵称 内容' 私聊，输入 'quit' 退出");

            // 主线程：读取控制台输入并发送
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                // 与服务端 LineBasedFrameDecoder 配套：一行输入必须带换行符才能形成完整消息。
                channel.writeAndFlush(line + System.lineSeparator());
                if ("quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
            }
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
