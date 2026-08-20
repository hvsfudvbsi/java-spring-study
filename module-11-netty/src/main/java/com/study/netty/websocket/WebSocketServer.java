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
        // 1. WebSocket 建立在 TCP 上，因此启动阶段仍然使用标准 NIO TCP 服务端结构。
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 2. 握手前的数据是 HTTP，因此先放 HTTP 编解码和聚合器。
                        ch.pipeline().addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(64 * 1024))
                                // 3. Handler 完成 HTTP Upgrade，并把后续数据转换成 WebSocketFrame。
                                .addLast(new WebSocketServerProtocolHandler(PATH))
                                // 4. 握手成功后的文本/二进制帧交给业务 Handler。
                                .addLast(new WebSocketFrameHandler());
                    }
                });

        // 5. 服务端开始监听；关闭监听 Channel 时同步停止 boss 和 worker。
        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        return channel;
    }
}
