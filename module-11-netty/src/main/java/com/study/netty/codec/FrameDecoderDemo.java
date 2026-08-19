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
            System.out.println("    -> " + msg.toString(CharsetUtil.UTF_8));
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
            System.out.println("    -> " + msg.toString(CharsetUtil.UTF_8));
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
            System.out.println("    -> " + msg.toString(CharsetUtil.UTF_8));
        }

        System.out.println();
        System.out.println("========== 4. LengthFieldBasedFrameDecoder 长度字段协议 ==========");
        // 协议格式: [4字节长度][消息体]。LengthFieldPrepender 负责写出时加长度头。
        EmbeddedChannel proto = new EmbeddedChannel(
                new LengthFieldPrepender(4),                       // 出站：消息前加 4 字节长度
                new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4) // 入站：按长度字段拆包
        );

        // 出站编码：LengthFieldPrepender（MessageToMessageEncoder）输出两条消息
        // [长度头] + [内容]，线上字节流连续，EmbeddedChannel 需分别读取后合并成帧
        proto.writeOutbound(Unpooled.copiedBuffer("协议消息A", CharsetUtil.UTF_8));
        proto.writeOutbound(Unpooled.copiedBuffer("协议消息B", CharsetUtil.UTF_8));
        ByteBuf framedA = Unpooled.wrappedBuffer((ByteBuf) proto.readOutbound(), (ByteBuf) proto.readOutbound());
        ByteBuf framedB = Unpooled.wrappedBuffer((ByteBuf) proto.readOutbound(), (ByteBuf) proto.readOutbound());
        System.out.println("  出站消息已加长度头（[4字节长度][内容]）");

        // 入站解码：模拟真实网络分批到达（一次 read 事件产出一条消息，
        // 剩余数据留在解码器累积缓冲区，等下一批数据到达继续解析）
        System.out.println("  按长度字段拆包（分批到达）:");
        proto.writeInbound(framedA.copy());
        System.out.println("    -> " + readMsg(proto));
        proto.writeInbound(framedB.copy());
        System.out.println("    -> " + readMsg(proto));
        framedA.release();
        framedB.release();
        proto.finish();
        line.finish();
        delim.finish();
        fixed.finish();
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
