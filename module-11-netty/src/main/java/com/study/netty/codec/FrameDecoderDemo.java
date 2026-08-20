package com.study.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.CharsetUtil;

/**
 * 粘包/拆包问题与四种解码器（面试必问！）
 *
 * 问题背景：TCP 是流式协议，没有消息边界。
 *   - 粘包：两个消息粘在一起到达（send("AB") send("CD") -> 收到 "ABCD"）
 *   - 拆包：一个消息被拆成两段到达（send("ABCD") -> 先收到 "AB" 再收到 "CD"）
 *
 * 四种解决方案（Netty 内置解码器）：
 *   1. LineBasedFrameDecoder    按行分隔（\n / \r\n）—— 文本协议
 *   2. DelimiterBasedFrameDecoder 按自定义分隔符 —— 如 ";"
 *   3. FixedLengthFrameDecoder  定长消息 —— 如每 3 字节一条
 *   4. LengthFieldBasedFrameDecoder 长度字段 + LengthFieldPrepender —— 二进制协议（最通用）
 *
 * 本示例用 EmbeddedChannel 演示：一次写入多条消息（粘包），观察正确拆分。
 */
public class FrameDecoderDemo {

    public static void main(String[] args) {
        System.out.println("========== 1. LineBasedFrameDecoder 按行拆包 ==========");
        // 一次写入 3 行（模拟粘包），解码器按 \n 拆成 3 条消息
        EmbeddedChannel line = new EmbeddedChannel(new LineBasedFrameDecoder(1024));
        line.writeInbound(Unpooled.copiedBuffer("hello\nworld\nnetty\n", CharsetUtil.UTF_8));
        System.out.println("  一次收到 3 行粘包，拆分为:");
        while (true) {
            ByteBuf msg = line.readInbound();
            if (msg == null) {
                break;
            }
            try {
                System.out.println("    -> " + msg.toString(CharsetUtil.UTF_8));
            } finally {
                // EmbeddedChannel 读出的 ByteBuf 仍然有引用计数，消费后必须释放。
                msg.release();
            }
        }

        System.out.println();
        System.out.println("========== 2. DelimiterBasedFrameDecoder 自定义分隔符 ==========");
        EmbeddedChannel delim = new EmbeddedChannel(new DelimiterBasedFrameDecoder(1024,
                Unpooled.copiedBuffer(";", CharsetUtil.UTF_8)));
        delim.writeInbound(Unpooled.copiedBuffer("user:1001;user:1002;user:1003;", CharsetUtil.UTF_8));
        System.out.println("  按 ';' 拆分为:");
        while (true) {
            ByteBuf msg = delim.readInbound();
            if (msg == null) {
                break;
            }
            try {
                System.out.println("    -> " + msg.toString(CharsetUtil.UTF_8));
            } finally {
                msg.release();
            }
        }

        System.out.println();
        System.out.println("========== 3. FixedLengthFrameDecoder 定长拆包 ==========");
        // 每 3 字节一条：abcdef -> abc / def
        EmbeddedChannel fixed = new EmbeddedChannel(new FixedLengthFrameDecoder(3));
        fixed.writeInbound(Unpooled.copiedBuffer("abcdef", CharsetUtil.UTF_8));
        System.out.println("  6 字节定长 3 拆分为:");
        while (true) {
            ByteBuf msg = fixed.readInbound();
            if (msg == null) {
                break;
            }
            try {
                System.out.println("    -> " + msg.toString(CharsetUtil.UTF_8));
            } finally {
                msg.release();
            }
        }

        System.out.println();
        System.out.println("========== 4. LengthFieldBasedFrameDecoder 长度字段协议 ==========");
        // 协议格式: [4字节长度][消息体]。编码器和解码器在真实 Pipeline 中通常同时存在，
        // 但在 EmbeddedChannel 中把两个方向拆成两个通道更容易看清数据流和资源所有权。
        EmbeddedChannel encoder = new EmbeddedChannel(new LengthFieldPrepender(4));
        EmbeddedChannel decoder = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4));

        // 1. 出站编码：每个业务消息都会变成一帧 [长度头][内容]。
        encoder.writeOutbound(Unpooled.copiedBuffer("协议消息A", CharsetUtil.UTF_8));
        encoder.writeOutbound(Unpooled.copiedBuffer("协议消息B", CharsetUtil.UTF_8));
        // EmbeddedChannel 中 LengthFieldPrepender 将长度头和原始内容分别放入出站队列，
        // 所以每条消息要把两个 ByteBuf 合并成线上看到的一帧。
        ByteBuf framedA = Unpooled.wrappedBuffer((ByteBuf) encoder.readOutbound(),
                (ByteBuf) encoder.readOutbound());
        ByteBuf framedB = Unpooled.wrappedBuffer((ByteBuf) encoder.readOutbound(),
                (ByteBuf) encoder.readOutbound());
        System.out.println("  出站消息已加长度头（[4字节长度][内容]）");

        // 2. 入站解码：两帧可能在同一次 TCP read 中到达，也可能分两次到达。
        //    这里分两次写入，突出“每次得到完整帧后才交给业务”的基本行为；
        //    CodecTest 还验证了两帧连续写入时的粘包处理。
        decoder.writeInbound(framedA.copy());
        decoder.writeInbound(framedB.copy());
        System.out.println("  按长度字段拆出完整消息:");
        System.out.println("    -> " + readMsg(decoder));
        System.out.println("    -> " + readMsg(decoder));

        framedA.release();
        framedB.release();
        encoder.finishAndReleaseAll();
        decoder.finishAndReleaseAll();
        line.finishAndReleaseAll();
        delim.finishAndReleaseAll();
        fixed.finishAndReleaseAll();
    }

    /** 读取一条入站消息并转为字符串 */
    private static String readMsg(EmbeddedChannel ch) {
        ByteBuf msg = ch.readInbound();
        if (msg == null) {
            return null;
        }
        try {
            return msg.toString(CharsetUtil.UTF_8);
        } finally {
            msg.release();
        }
    }
}
