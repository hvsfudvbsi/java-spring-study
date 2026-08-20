package com.study.netty.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/** 实操示例：HTTP Upgrade 到 WebSocket，再处理长连接文本帧。 */
public class WebSocketServer {

    public static final int DEFAULT_PORT = 18085;
    public static final String PATH = "/ws";

    public static void main(String[] args) throws Exception {
        Channel channel = start(DEFAULT_PORT);
        System.out.println("Netty WebSocket 服务器已启动: ws://127.0.0.1:" + DEFAULT_PORT + PATH);
        channel.closeFuture().sync();
    }

    /** 启动 WebSocket 服务。 */
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
                                .addLast(new WebSocketServerProtocolHandler(PATH))
                                .addLast(new WebSocketFrameHandler());
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
