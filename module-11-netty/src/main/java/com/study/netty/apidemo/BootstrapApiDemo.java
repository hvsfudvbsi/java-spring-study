package com.study.netty.apidemo;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bootstrap / ServerBootstrap 方法用例（常用 + 不常用）
 *
 * Bootstrap 是启动引导器（配置中心）：
 *   ServerBootstrap  服务端：绑定端口，接受连接（boss 线程 accept，worker 线程处理 IO）
 *   Bootstrap        客户端：连接远程服务
 *
 * 链式 API 三段式：
 *   1. group(...)  指定线程组
 *   2. channel(...) 指定 IO 模型（NIO/Epoll/KQueue/OIO）
 *   3. handler(...) 指定业务处理器（服务端还需 childHandler 处理新连接）
 *
 * 本示例自包含：内部起一个迷你回声服务端，客户端连上去发一条消息收回声。
 */
public class BootstrapApiDemo {

    /** 服务端属性（不常用 API 演示） */
    private static final AttributeKey<String> SERVER_ATTR = AttributeKey.valueOf("server-attr");
    /** 客户端属性（不常用 API 演示） */
    private static final AttributeKey<String> CLIENT_ATTR = AttributeKey.valueOf("client-attr");

    public static void main(String[] args) throws Exception {
        int port = 18079;

        System.out.println("========== ServerBootstrap 常用方法 ==========");

        // boss：接受新连接（1 个线程足够）；worker：处理连接的 IO
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup(2);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
                // ---- 常用方法 ----
                .group(boss, worker)                          // 线程组（boss + worker 双线程模型）
                .channel(NioServerSocketChannel.class)        // IO 模型：NIO（还有 Epoll/KQueue）
                .childHandler(new ChannelInitializer<SocketChannel>() {  // 每个新连接的处理链
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                // 回声：收到什么回什么（msg 直接转发出站，由 Netty 释放）
                                ctx.writeAndFlush(msg);
                            }
                        });
                    }
                });

        // ---- 不常用但有用的方法 ----
        serverBootstrap
                .handler(new LoggingHandler(LogLevel.INFO))     // boss 线程的 handler（通常打日志）
                .option(ChannelOption.SO_BACKLOG, 128)          // 服务端监听 socket 选项：等待队列长度
                .childOption(ChannelOption.SO_KEEPALIVE, true)  // 子连接选项：TCP 保活
                .childOption(ChannelOption.TCP_NODELAY, true)   // 子连接选项：禁用 Nagle（低延迟）
                .attr(SERVER_ATTR, "server-value")              // 服务端通道属性
                .childAttr(CLIENT_ATTR, "child-value")          // 子连接属性（可跨 handler 传递参数）
                .localAddress(port);                            // 监听地址端口

        System.out.println("  group/channel/childHandler + handler/option/childOption/attr/localAddress");

        Channel serverChannel = serverBootstrap.bind().sync().channel(); // bind 绑定 + sync 阻塞等待
        System.out.println("  服务端已启动: " + serverChannel.localAddress());

        System.out.println();
        System.out.println("========== Bootstrap 常用方法 ==========");

        EventLoopGroup clientGroup = new NioEventLoopGroup(1);
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap
                // ---- 常用方法 ----
                .group(clientGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                try {
                                    if (msg instanceof ByteBuf buf) {
                                        responses.offer(buf.toString(CharsetUtil.UTF_8));
                                    }
                                } finally {
                                    ReferenceCountUtil.release(msg); // 手动释放（入站消息）
                                }
                            }
                        });
                    }
                });

        // ---- 不常用但有用的方法 ----
        bootstrap
                .option(ChannelOption.TCP_NODELAY, true)             // 连接选项
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)  // 连接超时 3 秒
                .attr(CLIENT_ATTR, "client-value")                   // 通道属性
                .remoteAddress("127.0.0.1", port)                    // 远程地址
                .validate();                                         // 校验配置完整性（自动调用）

        System.out.println("  group/channel/handler + option/attr/remoteAddress/validate");

        // ---- 两种连接方式 ----
        // 方式一（常用）：sync 阻塞等待连接成功
        Channel clientChannel = bootstrap.connect().sync().channel();
        System.out.println("  connect().sync() 连接成功: " + clientChannel.remoteAddress());

        // 方式二（不常用）：listener 回调，不阻塞
        ChannelFuture future2 = bootstrap.connect("127.0.0.1", port);
        future2.addListener((GenericFutureListener<ChannelFuture>) f ->
                System.out.println("  回调式连接: 成功=" + f.isSuccess()));
        future2.sync();
        // 回调式连接只用于演示非阻塞监听；发起关闭即可，不在这里同步等待第二条连接。
        future2.channel().close();

        // ---- 发送与接收 ----
        // Pipeline 没有 StringEncoder，因此这里显式把字符串编码成 ByteBuf 后再写出。
        clientChannel.writeAndFlush(Unpooled.copiedBuffer("hello netty", CharsetUtil.UTF_8)).sync();
        String response = responses.poll(5, TimeUnit.SECONDS);
        System.out.println("  收到服务端回声: " + response);

        // ---- 通道属性读取（attr 的用途） ----
        System.out.println("  客户端属性: " + clientChannel.attr(CLIENT_ATTR).get());

        // ---- 清理 ----
        clientChannel.close().sync();
        serverChannel.close().sync();
        boss.shutdownGracefully();
        worker.shutdownGracefully();
        clientGroup.shutdownGracefully();
        System.out.println("\n  Bootstrap/ServerBootstrap 演示完毕");
    }
}
