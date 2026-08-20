package com.study.netty.apidemo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
 * ByteBuf 方法用例（常用 + 不常用）
 *
 * ByteBuf 是 Netty 的字节容器，替代 JDK 的 ByteBuffer，核心优势：
 *   1. 读写双指针（readerIndex / writerIndex），无需 flip()
 *   2. 池化 + 引用计数（retain/release），避免 GC 压力
 *   3. 零拷贝（slice/duplicate/CompositeByteBuf）
 *
 * 内存模型：
 *   [可丢弃区][可读区 readableBytes][可写区 writableBytes][可扩容区]
 *   readerIndex -> writerIndex -> capacity -> maxCapacity
 */
public class ByteBufApiDemo {

    public static void main(String[] args) {
        System.out.println("========== ByteBuf 常用方法 ==========");
        // 创建：堆内存（默认）
        ByteBuf buf = Unpooled.buffer();
        // Unpooled.directBuffer()      直接内存（零拷贝、堆外）
        // Unpooled.copiedBuffer(...)   从已有数据创建
        // Unpooled.wrappedBuffer(...)  包装已有数组（零拷贝，不复制）

        // ---- 写入（移动 writerIndex） ----
        buf.writeInt(100);                                // 写 4 字节 int
        buf.writeBytes("hello".getBytes(CharsetUtil.UTF_8)); // 写字节数组
        buf.writeByte(0x01);                              // 写 1 字节
        buf.writeShort((short) 2);                        // 写 2 字节
        buf.writeLong(99L);                               // 写 8 字节
        buf.writeCharSequence("netty", CharsetUtil.UTF_8);  // 写字符串

        System.out.println("  可读字节 readableBytes = " + buf.readableBytes()
                + "，可写字节 writableBytes = " + buf.writableBytes());

        // ---- 读取（移动 readerIndex） ----
        int v = buf.readInt();                            // 读 4 字节
        byte[] bytes = new byte[5];
        buf.readBytes(bytes);                             // 读 5 字节到数组
        byte b = buf.readByte();                          // 读 1 字节
        short s = buf.readShort();                        // 读 2 字节
        long l = buf.readLong();                          // 读 8 字节
        CharSequence cs = buf.readCharSequence(5, CharsetUtil.UTF_8); // 读 5 字节为字符串
        System.out.println("  读取结果: int=" + v + ", str=" + new String(bytes)
                + ", byte=" + b + ", short=" + s + ", long=" + l + ", charSeq=" + cs);

        // ---- 引用计数（Netty 内存管理核心） ----
        buf.retain();        // 引用计数 +1（共享时防止被释放）
        buf.release();       // 引用计数 -1（归零时回收内存）
        System.out.println("  refCnt = " + buf.refCnt() + "（retain/release 成对使用）");

        // ---- 视图与拷贝 ----
        ByteBuf copy = buf.copy();        // 深拷贝：完全独立的新缓冲区
        // retainedSlice/retainedDuplicate 除了共享内存，还各自增加引用计数，便于示例最后分别释放。
        ByteBuf slice = buf.retainedSlice();      // 切片：共享内存、独立索引（零拷贝）
        ByteBuf duplicate = buf.retainedDuplicate(); // 复制：共享内存、独立索引，整个缓冲区
        System.out.println("  copy 独立=" + (copy != buf) + "，slice 可读=" + slice.readableBytes()
                + "，duplicate 可读=" + duplicate.readableBytes());
        copy.release();

        // ---- 索引与容量 ----
        buf.readerIndex(0);   // 回到读起点（重读）
        buf.writerIndex(buf.capacity()); // 直接把写指针移到末尾
        buf.clear();          // 重置 reader=0, writer=0（不清数据，只复位指针）
        buf.capacity(128);    // 调整容量
        System.out.println("  capacity=" + buf.capacity() + ", maxCapacity=" + buf.maxCapacity());

        System.out.println();
        System.out.println("========== ByteBuf 不常用但有用的方法 ==========");

        // ---- 标记/重置（粘包拆包时很常用） ----
        buf.writeBytes("abcdef".getBytes(CharsetUtil.UTF_8));
        buf.markReaderIndex();        // 标记当前读位置
        buf.readByte();               // 读 1 字节
        buf.resetReaderIndex();       // 回到标记位置（数据不足时回退，见 codec 包）
        System.out.println("  markReaderIndex/resetReaderIndex: 复位后可重读 = " + (char) buf.getByte(0));

        // ---- 绝对定位读写（不移动指针） ----
        buf.getByte(0);               // 绝对读
        buf.getInt(0);                // 绝对读 4 字节
        buf.setByte(0, (byte) 'X');   // 绝对写（覆盖）
        buf.setInt(0, 1);
        System.out.println("  getByte/setByte 绝对定位读写，不移动 readerIndex/writerIndex");

        // ---- 跳过与整理 ----
        buf.skipBytes(2);             // 跳过 2 字节（读取时丢弃无用数据）
        buf.discardReadBytes();       // 丢弃已读数据，腾出可写空间（移动数据，有成本）
        System.out.println("  skipBytes/discardReadBytes 后 writableBytes=" + buf.writableBytes());

        // ---- 容量保障 ----
        buf.ensureWritable(1024);     // 确保至少还有 1024 字节可写（自动扩容）
        System.out.println("  ensureWritable(1024) 后 capacity=" + buf.capacity());

        // ---- 查找（粘包拆包手动实现时常用） ----
        int idx = buf.indexOf(buf.readerIndex(), buf.writerIndex(), (byte) 'c');
        int idx2 = buf.bytesBefore((byte) 'd');
        buf.forEachByte(value -> value != 0); // ByteProcessor 遍历（找到返回下标）
        System.out.println("  indexOf('c')=" + idx + ", bytesBefore('d')=" + idx2);

        // ---- 内存属性 ----
        buf.hasArray();               // 是否为堆内存（可 array()）
        buf.array();                  // 直接拿底层字节数组（仅堆内存）
        buf.isDirect();               // 是否直接内存
        buf.nioBuffer();              // 转为 JDK ByteBuffer（与 NIO 互通）
        buf.isReadable();             // 是否有可读数据
        buf.isWritable();             // 是否可写
        buf.readerIndex();
        buf.writerIndex();
        buf.unwrap();                 // 返回底层缓冲区（复合缓冲时）
        buf.toString(CharsetUtil.UTF_8); // 剩余可读内容转字符串
        buf.compareTo(buf);           // 内容比较（按无符号字节比较）

        System.out.println("  内存属性: hasArray=" + buf.hasArray()
                + ", isDirect=" + buf.isDirect() + ", isReadable=" + buf.isReadable());

        // ---- 其他不常用：先准备足够的数据，再演示不同宽度的读取 API ----
        // 前面的查找和 discardReadBytes 可能已经消耗了可读区，不能假设当前还有足够字节。
        buf.clear();
        buf.writeZero(4);             // 写 4 个零字节，供 readUnsignedInt 读取
        buf.writeInt(0x01020304);     // 再写 4 字节，供 readSlice/readMedium 继续读取
        buf.writeMedium(0x050607);    // 写 3 字节 medium 类型数据
        buf.writeBytes("xy".getBytes(CharsetUtil.UTF_8));
        buf.readUnsignedInt();        // 读无符号 int（0 ~ 2^32-1）
        buf.readSlice(2);             // 读 2 字节并返回切片视图（零拷贝，视图不单独 release）
        buf.readMedium();             // 读 3 字节（medium）
        buf.markWriterIndex();        // 标记当前 writerIndex，适合回退到某个写入位置
        buf.resetWriterIndex();       // 恢复到刚才的 writerIndex
        buf.forEachByteDesc(value -> value != 0); // 从后往前遍历

        // 释放内存（池化缓冲必须释放！）。retainedSlice/retainedDuplicate 各自拥有一个引用计数。
        buf.release();
        slice.release();
        duplicate.release();
        System.out.println("\n  ByteBuf 演示完毕（所有缓冲已释放）");
    }
}
