package com.study.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    @DisplayName("引用计数：retain 加 1，release 减 1，归零后不能再访问")
    void referenceCount() {
        ByteBuf buf = Unpooled.buffer();
        assertEquals(1, buf.refCnt());

        buf.retain();  // 借用方 +1
        assertEquals(2, buf.refCnt());
        buf.release(); // 借出方归还
        assertEquals(1, buf.refCnt());

        buf.release(); // 归零：底层内存被回收
        assertEquals(0, buf.refCnt());
    }

    @Test
    @DisplayName("ensureWritable：容量不足时自动扩容（受 maxCapacity 限制）")
    void ensureWritableGrowsCapacity() {
        // 初始 capacity 为 0 的小缓冲区，便于观察扩容
        ByteBuf buf = Unpooled.buffer(0, 64);
        assertEquals(0, buf.capacity());

        buf.ensureWritable(16);
        assertTrue(buf.capacity() >= 16, "扩容后至少能容纳 16 字节，实际: " + buf.capacity());

        // 超出 maxCapacity 时扩容失败：抛 IndexOutOfBoundsException 拒绝分配
        assertThrows(IndexOutOfBoundsException.class, () -> buf.ensureWritable(1024),
                "超过 maxCapacity=64 扩容应被拒绝");
        buf.release();
    }

    @Test
    @DisplayName("discardReadBytes：丢弃已读部分，回收可写空间但保留未读数据")
    void discardReadBytesReclaimsSpace() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);
        buf.writeInt(2);
        buf.readInt(); // 读走第一个 int，readerIndex 前进

        assertEquals(4, buf.readableBytes());
        assertEquals(4, buf.readerIndex());

        buf.discardReadBytes();
        // 已读的 4 字节被回收：readerIndex 归零，未读的 int 仍在开头
        assertEquals(0, buf.readerIndex());
        assertEquals(4, buf.readableBytes());
        assertEquals(2, buf.getInt(0), "未读数据被前移到开头");
        buf.release();
    }

    @Test
    @DisplayName("readUnsignedInt：负数按无符号解释为 0~2^32-1 的正数")
    void readUnsignedInt() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(-1); // 0xFFFFFFFF，按有符号是 -1

        assertEquals(-1, buf.getInt(0));
        assertEquals(0xFFFFFFFFL, buf.readUnsignedInt(), "无符号读应是 4294967295");
        buf.release();
    }

    @Test
    @DisplayName("writeZero：写入 N 个 0 字节，可用于占位或对齐")
    void writeZeroPads() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0xAB);
        buf.writeZero(3);

        assertEquals(4, buf.readableBytes());
        // getByte 返回有符号 byte（0xAB 会溢出为负），用 getUnsignedByte 验证原值
        assertEquals(0xAB, buf.getUnsignedByte(0));
        assertEquals(0, buf.getUnsignedByte(1));
        assertEquals(0, buf.getUnsignedByte(3));
        buf.release();
    }
}
