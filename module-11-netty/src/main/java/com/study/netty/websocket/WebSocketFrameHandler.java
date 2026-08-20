package com.study.netty.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/** WebSocket 业务处理器：回显文本帧，演示长连接消息处理。 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 1. 文本帧直接转成新的文本帧回显；不能把同一个入站帧对象直接重复发送。
        if (frame instanceof TextWebSocketFrame textFrame) {
            ctx.writeAndFlush(new TextWebSocketFrame("echo: " + textFrame.text()));
            return;
        }

        // 2. Ping/Pong 是 WebSocket 协议层心跳。retain 后才能把入站内容交给新的出站 Pong 帧，
        //    因为 SimpleChannelInboundHandler 在方法返回后会自动释放原入站帧。
        if (frame instanceof PingWebSocketFrame pingFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            return;
        }

        // 3. 二进制帧也可以回显；retain 的原因与 Ping/Pong 相同。
        if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            ctx.writeAndFlush(new BinaryWebSocketFrame(binaryFrame.content().retain()));
            return;
        }

        // 4. 其他帧在学习示例中返回可读错误；生产代码还应明确处理 Close 帧和协议扩展。
        ctx.writeAndFlush(new TextWebSocketFrame(
                "unsupported frame: " + frame.getClass().getSimpleName()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
