package com.study.nettyboot;

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
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * 在 Spring Boot（内嵌 Tomcat）进程内启动的 Netty TCP 服务。
 *
 * 设计要点：
 *   - ApplicationRunner：Spring 容器就绪后自动启动 Netty，端口由
 *     netty.server.port 配置（默认 19090，0 = 随机端口便于测试）；
 *   - Netty 的 EventLoop 线程与 Tomcat 的请求线程池完全独立，
 *     长连接流量走 Netty，HTTP/REST 走 Tomcat，互不占线程；
 *   - @PreDestroy：应用/测试关闭时优雅停止 Netty。
 */
@Component
public class NettyTcpServer implements ApplicationRunner {

    private final NettyTcpServerHandler handler;

    @Value("${netty.server.port:19090}")
    private int port;

    @Value("${netty.server.workers:4}")
    private int workers;

    private volatile Channel serverChannel;

    public NettyTcpServer(NettyTcpServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup(workers);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                // 按行协议：先恢复消息边界，再转字符串，最后交给业务 Handler
                                new LineBasedFrameDecoder(64 * 1024),
                                new StringDecoder(CharsetUtil.UTF_8),
                                new StringEncoder(CharsetUtil.UTF_8),
                                handler); // Spring 注入的共享 Handler
                    }
                });
        serverChannel = bootstrap.bind(port).sync().channel();
        serverChannel.closeFuture().addListener(future -> {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        });
        System.out.println("[Netty] TCP 服务已启动: " + serverChannel.localAddress());
    }

    /** 实际绑定的端口（netty.server.port=0 时为随机端口，供测试读取）。 */
    public int localPort() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
    }
}
