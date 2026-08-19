package com.study.netty;

import com.study.netty.codec.CustomCodecDemo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 粘包拆包测试：用 EmbeddedChannel 验证四种解码器和自定义编解码器
 * （无需真实网络，直接在内存中验证协议处理）
 */
class CodecTest {

    /** 读取一条入站消息并转为字符串（同时释放） */
    private static String readString(EmbeddedChannel ch) {
        ByteBuf buf = ch.readInbound();
        if (buf == null) {
            return null;
        }
        try {
            return buf.toString(CharsetUtil.UTF_8);
        } finally {
            buf.release();
        }
    }

    @Test
    @DisplayName("LineBasedFrameDecoder 按行拆包（粘包场景）")
    void lineBased() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(1024));
        // 一次写入 3 行（粘包），应拆成 3 条
        ch.writeInbound(Unpooled.copiedBuffer("a\nb\nc\n", CharsetUtil.UTF_8));
        assertEquals("a", readString(ch));
        assertEquals("b", readString(ch));
        assertEquals("c", readString(ch));
        assertNull(readString(ch));
        ch.finish();
    }

    @Test
    @DisplayName("DelimiterBasedFrameDecoder 自定义分隔符")
    void delimiterBased() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(1024,
                Unpooled.copiedBuffer(";", CharsetUtil.UTF_8)));
        ch.writeInbound(Unpooled.copiedBuffer("x;y;z;", CharsetUtil.UTF_8));
        assertEquals("x", readString(ch));
        assertEquals("y", readString(ch));
        assertEquals("z", readString(ch));
        ch.finish();
    }

    @Test
    @DisplayName("FixedLengthFrameDecoder 定长拆包")
    void fixedLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new FixedLengthFrameDecoder(3));
        ch.writeInbound(Unpooled.copiedBuffer("abcdef", CharsetUtil.UTF_8));
        assertEquals("abc", readString(ch));
        assertEquals("def", readString(ch));
        ch.finish();
    }

    @Test
    @DisplayName("LengthFieldBasedFrameDecoder 长度字段协议（粘包场景）")
    void lengthField() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new LengthFieldPrepender(4),                        // 出站加长度头
                new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4)); // 入站按长度拆包

        // 出站：LengthFieldPrepender（MessageToMessageEncoder）输出两条消息：
        // [长度头] + [内容]，线上字节流连续，但 EmbeddedChannel 需分别读取后合并
        ch.writeOutbound(Unpooled.copiedBuffer("AB", CharsetUtil.UTF_8));
        ch.writeOutbound(Unpooled.copiedBuffer("CD", CharsetUtil.UTF_8));
        ByteBuf framed1 = Unpooled.wrappedBuffer((ByteBuf) ch.readOutbound(), (ByteBuf) ch.readOutbound()); // 长度头+内容A
        ByteBuf framed2 = Unpooled.wrappedBuffer((ByteBuf) ch.readOutbound(), (ByteBuf) ch.readOutbound()); // 长度头+内容B

        // 入站：模拟真实网络分批到达（ByteToMessageDecoder 一次 channelRead
        // 只产出一条消息，剩余留在累积缓冲区等下一批数据）
        ch.writeInbound(framed1.copy());
        assertEquals("AB", readString(ch));
        ch.writeInbound(framed2.copy());
        assertEquals("CD", readString(ch));
        framed1.release();
        framed2.release();
        ch.finish();
    }

    @Test
    @DisplayName("自定义编解码器：半帧到达时等待，补齐后产出")
    void customCodecSplitPacket() {
        EmbeddedChannel ch = new EmbeddedChannel(new CustomCodecDemo.StringLengthDecoder());
        String msg = "半帧测试消息";
        byte[] bytes = msg.getBytes(CharsetUtil.UTF_8);

        // 构造 [4字节长度][内容]
        ByteBuf frame = Unpooled.buffer();
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);

        // 模拟拆包：先到一半
        int half = frame.readableBytes() / 2;
        ch.writeInbound(frame.readSlice(half).copy());
        assertNull(ch.readInbound(), "半帧数据不应产出消息，应等待补齐");

        // 补齐剩余：现在应产出完整消息
        ch.writeInbound(frame.readSlice(frame.readableBytes()).copy());
        assertEquals(msg, ch.readInbound());
        frame.release();
        ch.finish();
    }
}
