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
        // 1. URI 可能包含查询字符串；当前示例按路径路由，因此先去掉 '?name=value' 部分。
        String path = request.uri().split("\\?", 2)[0];
        // 2. 先计算状态码和响应正文，再统一创建响应对象，便于观察路由和响应的对应关系。
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

        // 3. 把 Java 字符串编码成 UTF-8 ByteBuf，构造完整 HTTP/1.1 响应。
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        // 4. Content-Type 告诉客户端如何解释正文；Content-Length 让客户端知道正文边界。
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        // 5. Keep-Alive 时复用当前 TCP 连接；否则响应写出后关闭连接。
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
        }

        // 6. writeAndFlush 沿出站 Pipeline 编码并发送响应；Future 用于在发送完成后关闭非长连接。
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
