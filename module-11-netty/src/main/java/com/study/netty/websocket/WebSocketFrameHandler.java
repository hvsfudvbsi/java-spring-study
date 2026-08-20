package com.study.netty.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/** WebSocket 业务处理器：回显文本帧，演示长连接消息处理。 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame textFrame) {
            ctx.writeAndFlush(new TextWebSocketFrame("echo: " + textFrame.text()));
        } else {
            ctx.writeAndFlush(new TextWebSocketFrame(
                    "unsupported frame: " + frame.getClass().getSimpleName()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
