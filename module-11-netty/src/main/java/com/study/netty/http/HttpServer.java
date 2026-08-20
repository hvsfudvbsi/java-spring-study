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
        // 1. boss 线程只负责接收新的 TCP 连接，worker 线程负责已建立连接的读写。
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        // 2. ServerBootstrap 是服务端启动器，用来把线程组、Channel 类型和子连接 Pipeline 组装起来。
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                // 3. NioServerSocketChannel 代表监听端口的 NIO ServerSocketChannel。
                .channel(NioServerSocketChannel.class)
                // 4. childHandler 只作用于每一个新建立的客户端 SocketChannel。
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 5. Codec 负责字节与 HTTP 对象互转。
                        ch.pipeline().addLast(new HttpServerCodec())
                                // 6. HTTP 请求可能分段到达，Aggregator 把多个 HttpContent 合成 FullHttpRequest。
                                .addLast(new HttpObjectAggregator(64 * 1024))
                                // 7. 聚合后的完整请求交给业务 Handler 做路由和响应。
                                .addLast(new HttpServerHandler());
                    }
                });

        // 8. bind 是异步操作；sync 等待绑定成功，避免返回一个尚未监听的 Channel。
        Channel channel = bootstrap.bind(port).sync().channel();
        // 9. 关闭监听 Channel 后再优雅关闭两个 EventLoopGroup，确保进程可以退出。
        channel.closeFuture().addListener(future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        return channel;
    }
}
