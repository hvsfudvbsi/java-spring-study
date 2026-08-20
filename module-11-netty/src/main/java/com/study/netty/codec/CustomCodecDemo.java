package com.study.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * 自定义编解码器（协议设计的核心技能）
 *
 * 协议：String <-> [4字节长度][UTF-8字节内容]
 *
 * 编码器（出站）：MessageToByteEncoder<String>
 *   业务对象 -> 字节流（写出时调用 encode）
 *
 * 解码器（入站）：ByteToMessageDecoder
 *   字节流 -> 业务对象（收到数据时调用 decode）
 *   关键：数据不够时不要消费（markReaderIndex + resetReaderIndex 回退），
 *   等下一批数据到齐再处理 —— 这就是拆包的正确处理方式！
 */
public class CustomCodecDemo {

    /** 自定义编码器：String -> [4字节长度][内容]（public 供测试复用） */
    public static class StringLengthEncoder extends MessageToByteEncoder<String> {
        @Override
        protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) {
            byte[] bytes = msg.getBytes(CharsetUtil.UTF_8);
            out.writeInt(bytes.length);   // 4 字节长度头
            out.writeBytes(bytes);        // 内容
        }
    }

    /** 自定义解码器：[4字节长度][内容] -> String（public 供测试复用） */
    public static class StringLengthDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 4) {
                return; // 长度头都没到齐，等下一批数据
            }
            in.markReaderIndex();          // 标记（防止长度不足时已消费）
            int length = in.readInt();
            if (in.readableBytes() < length) {
                in.resetReaderIndex();     // 内容不足，回退，等下一批数据
                return;
            }
            byte[] bytes = new byte[length];
            in.readBytes(bytes);
            out.add(new String(bytes, CharsetUtil.UTF_8)); // 产出业务对象
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 自定义编解码器 ==========");

        EmbeddedChannel channel = new EmbeddedChannel(new StringLengthDecoder(), new StringLengthEncoder());

        // ---- 出站：业务对象 -> 字节流 ----
        channel.writeOutbound("你好，Netty");
        ByteBuf encoded = channel.readOutbound();
        System.out.println("  编码结果: 长度头=" + encoded.getInt(0) + "，总字节=" + encoded.readableBytes());

        // ---- 入站：字节流 -> 业务对象（完整一帧） ----
        channel.writeInbound(encoded.copy());
        System.out.println("  解码（完整帧）: " + channel.readInbound());

        // ---- 入站：模拟拆包（一帧数据分两批到达） ----
        ByteBuf frame = encoded.copy();
        int half = frame.readableBytes() / 2;
        ByteBuf firstHalf = frame.readSlice(half);      // 第一批：半帧
        ByteBuf secondHalf = frame.readSlice(frame.readableBytes()); // 第二批：剩余
        channel.writeInbound(firstHalf.copy());         // 先到一半（不足，解码器应等待）
        System.out.println("  第一批半帧到达，解码器等待...（readInbound=" + channel.readInbound() + "）");
        channel.writeInbound(secondHalf.copy());        // 补齐后产出完整消息
        System.out.println("  第二批补齐后解码: " + channel.readInbound());
        // readSlice 只是 frame 的视图；两批数据复制完成后，原始 frame 才能释放。
        frame.release();

        // ---- 入站：模拟粘包（两帧连在一起到达） ----
        channel.writeInbound(wrapTwoFrames(encoded, encoded)); // 两帧拼接
        System.out.println("  粘包（两帧一起）拆分为:");
        Object m;
        while ((m = channel.readInbound()) != null) {
            System.out.println("    -> " + m);
        }

        encoded.release();
        // finishAndReleaseAll 同时关闭 EmbeddedChannel，并清理尚未消费的入站/出站消息。
        channel.finishAndReleaseAll();
    }

    /** 拼接两个 ByteBuf（演示粘包） */
    private static ByteBuf wrapTwoFrames(ByteBuf a, ByteBuf b) {
        return io.netty.buffer.Unpooled.wrappedBuffer(a.copy(), b.copy());
    }
}
