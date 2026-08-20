package com.study.netty.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** 实操示例：使用 Netty 编码 HTTP 请求并读取完整响应。 */
public class HttpClient {

    public static void main(String[] args) throws Exception {
        String response = get("127.0.0.1", HttpServer.DEFAULT_PORT, "/hello");
        System.out.println("HTTP 响应: " + response);
    }

    /** 发起 GET 请求并返回响应正文。 */
    public static String get(String host, int port, String path) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new HttpClientCodec())
                                    .addLast(new HttpObjectAggregator(64 * 1024))
                                    .addLast(new HttpResponseHandler(responses));
                        }
                    });

            Channel channel = bootstrap.connect(host, port).sync().channel();
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, path);
            request.headers()
                    .set(HttpHeaderNames.HOST, host)
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            channel.writeAndFlush(request).sync();

            String response = responses.poll(5, TimeUnit.SECONDS);
            channel.close().sync();
            return response;
        } finally {
            group.shutdownGracefully();
        }
    }

    private static class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final BlockingQueue<String> responses;

        private HttpResponseHandler(BlockingQueue<String> responses) {
            this.responses = responses;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
            responses.offer(response.status().code() + " "
                    + response.content().toString(CharsetUtil.UTF_8));
        }
    }
}
