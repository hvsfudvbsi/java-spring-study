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
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    @DisplayName("LineBasedFrameDecoder 拆包：一行分两批到达，补齐换行才产出")
    void lineBasedSplitPacket() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(1024));
        // 第一批：只有 "hel"，没有换行符 -> 不应产出
        ch.writeInbound(Unpooled.copiedBuffer("hel", CharsetUtil.UTF_8));
        assertNull(readString(ch), "没有换行符不应产出消息");

        // 第二批：补上 "lo\n"，此时累计为 "hello\n" -> 产出完整一行
        ch.writeInbound(Unpooled.copiedBuffer("lo\n", CharsetUtil.UTF_8));
        assertEquals("hello", readString(ch));
        ch.finish();
    }

    @Test
    @DisplayName("FixedLengthFrameDecoder 拆包：不足定长时等待，凑满才产出")
    void fixedLengthSplitPacket() {
        EmbeddedChannel ch = new EmbeddedChannel(new FixedLengthFrameDecoder(3));
        // 第一批 2 字节 < 3 -> 不产出
        ch.writeInbound(Unpooled.copiedBuffer("ab", CharsetUtil.UTF_8));
        assertNull(readString(ch), "不足 3 字节不应产出");

        // 第二批 4 字节：累计 "abcdef"，先产出 "abc"，剩余 "def" 留在缓冲区
        ch.writeInbound(Unpooled.copiedBuffer("cdef", CharsetUtil.UTF_8));
        assertEquals("abc", readString(ch));

        // 第三批 1 字节：累计 "def" + "g"，产出 "def"，剩余 "g" 等下一批
        ch.writeInbound(Unpooled.copiedBuffer("g", CharsetUtil.UTF_8));
        assertEquals("def", readString(ch));
        ch.finish();
    }

    @Test
    @DisplayName("LengthFieldBasedFrameDecoder 超长帧被拒绝（防内存攻击）")
    void lengthFieldRejectsOversizedFrame() {
        // maxFrameLength=8：长度头声明的帧超过 8 字节必须拒绝，防止恶意超大分配
        EmbeddedChannel ch = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(8, 0, 4, 0, 4));

        ByteBuf oversized = Unpooled.buffer();
        oversized.writeInt(100);  // 长度头声明 100 字节（超过 maxFrameLength=8）
        oversized.writeBytes(new byte[100]);

        assertThrows(TooLongFrameException.class, () -> ch.writeInbound(oversized),
                "超长帧应抛出 TooLongFrameException");
        // 解码器失败路径会自行释放入站缓冲区，这里只需兜底释放未释放的部分
        if (oversized.refCnt() > 0) {
            oversized.release();
        }
        ch.finish();
    }

    @Test
    @DisplayName("自定义解码器粘包：两帧一次到达，循环产出两条消息")
    void customCodecStickyPackets() {
        EmbeddedChannel ch = new EmbeddedChannel(new CustomCodecDemo.StringLengthDecoder());

        // 构造两帧 [长度][内容] 拼接在一起（模拟粘包）
        ByteBuf frames = Unpooled.buffer();
        byte[] a = "第一帧".getBytes(CharsetUtil.UTF_8);
        byte[] b = "第二帧".getBytes(CharsetUtil.UTF_8);
        frames.writeInt(a.length).writeBytes(a);
        frames.writeInt(b.length).writeBytes(b);

        ch.writeInbound(frames); // 一次写入两帧
        assertEquals("第一帧", ch.readInbound());
        assertEquals("第二帧", ch.readInbound());
        assertNull(ch.readInbound(), "不应有多余消息");
        ch.finish();
    }

    @Test
    @DisplayName("自定义编码器：String 编码为 [4字节长度][UTF-8内容]")
    void customCodecEncoderWritesLengthHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new CustomCodecDemo.StringLengthEncoder());
        String msg = "编码测试";

        ch.writeOutbound(msg);
        ByteBuf encoded = ch.readOutbound();
        try {
            byte[] bytes = msg.getBytes(CharsetUtil.UTF_8);
            assertEquals(bytes.length, encoded.getInt(0), "长度头应等于内容字节数");
            assertEquals(4 + bytes.length, encoded.readableBytes());
            assertEquals(msg, encoded.slice(4, bytes.length).toString(CharsetUtil.UTF_8), "内容应为原文");
        } finally {
            encoded.release();
        }
        ch.finish();
    }
}
