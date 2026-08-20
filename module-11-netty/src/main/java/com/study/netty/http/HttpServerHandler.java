package com.study.netty.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/** HTTP 请求处理器：展示请求路径、状态码、响应头和 Keep-Alive。 */
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = request.uri().split("\\?", 2)[0];
        HttpResponseStatus status;
        String body;

        if ("/hello".equals(path)) {
            status = HttpResponseStatus.OK;
            body = "Hello from Netty HTTP";
        } else if ("/health".equals(path)) {
            status = HttpResponseStatus.OK;
            body = "{\"status\":\"UP\"}";
        } else {
            status = HttpResponseStatus.NOT_FOUND;
            body = "Not Found: " + path;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
        }

        var future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(f -> ctx.close());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
