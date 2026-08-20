package com.study.netty.chat;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * 群聊出站编码器：给每条文本消息补一个换行符。
 *
 * 与 LineBasedFrameDecoder 配套：服务器端用 LineBasedFrameDecoder 按行解码客户端消息，
 * 出站侧必须保证每条消息都以换行符结尾，客户端才能按行恢复消息边界。
 * 否则 TCP 粘包时多条消息会拼成一个字符串，客户端无法逐条还原。
 */
public class ChatLineEncoder extends MessageToMessageEncoder<String> {

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, List<Object> out) {
        out.add(msg + "\n");
    }
}
