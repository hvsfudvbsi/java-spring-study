package com.study.netty.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/** 实操示例：基于 Netty Pipeline 提供最小 HTTP 服务。 */
public class HttpServer {

    public static final int DEFAULT_PORT = 18083;

    public static void main(String[] args) throws Exception {
        Channel channel = start(DEFAULT_PORT);
        System.out.println("Netty HTTP 服务器已启动: " + channel.localAddress());
        System.out.println("访问 http://127.0.0.1:" + DEFAULT_PORT + "/hello");
        channel.closeFuture().sync();
    }

    /** 启动 HTTP 服务，返回服务端 Channel 供测试或调用方关闭。 */
    public static Channel start(int port) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(64 * 1024))
                                .addLast(new HttpServerHandler());
                    }
                });

        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        return channel;
    }
}
