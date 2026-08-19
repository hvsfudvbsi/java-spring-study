package com.study.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ByteBuf 核心行为测试：读写指针、切片共享内存、标记复位
 */
class ByteBufApiTest {

    @Test
    @DisplayName("读写指针移动：read 后可读区减少")
    void readWrite() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(42);
        buf.writeBytes("hi".getBytes(CharsetUtil.UTF_8));

        assertEquals(6, buf.readableBytes());   // 4(int) + 2(hi)
        assertEquals(42, buf.readInt());
        assertEquals(2, buf.readableBytes());

        byte[] bytes = new byte[2];
        buf.readBytes(bytes);
        assertEquals("hi", new String(bytes, CharsetUtil.UTF_8));
        assertFalse(buf.isReadable());          // 读完了
        buf.release();
    }

    @Test
    @DisplayName("slice 零拷贝：切片与原缓冲区共享内存")
    void sliceSharesMemory() {
        ByteBuf buf = Unpooled.copiedBuffer("hello".getBytes(CharsetUtil.UTF_8));
        ByteBuf slice = buf.slice(0, 3);

        assertEquals("hel", slice.toString(CharsetUtil.UTF_8));

        // 修改切片会影响原缓冲区（共享底层内存，零拷贝）
        slice.setByte(0, (byte) 'H');
        assertEquals('H', buf.getByte(0));

        buf.release();
    }

    @Test
    @DisplayName("markReaderIndex/resetReaderIndex：可回退重读")
    void markAndReset() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        buf.writeInt(2);

        buf.markReaderIndex();
        assertEquals(1, buf.readInt());
        buf.resetReaderIndex();      // 回到标记位置
        assertEquals(1, buf.readInt()); // 重读成功

        buf.clear();                 // 复位指针（数据还在，只是不可读）
        assertEquals(0, buf.readableBytes());
        buf.release();
    }

    @Test
    @DisplayName("getByte/setByte 绝对读写不移动指针")
    void absoluteAccess() {
        ByteBuf buf = Unpooled.copiedBuffer("abc".getBytes(CharsetUtil.UTF_8));
        buf.setByte(0, (byte) 'X');  // 绝对写
        assertEquals('X', buf.getByte(0)); // 绝对读
        assertEquals(3, buf.readableBytes()); // 指针没动
        buf.release();
    }

    @Test
    @DisplayName("copy 深拷贝与原缓冲区完全独立")
    void copyIsIndependent() {
        ByteBuf buf = Unpooled.copiedBuffer("abc".getBytes(CharsetUtil.UTF_8));
        ByteBuf copy = buf.copy();
        copy.setByte(0, (byte) 'Z');
        assertEquals('a', buf.getByte(0)); // 原缓冲区不受影响
        copy.release();
        buf.release();
    }
}
