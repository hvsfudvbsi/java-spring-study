package com.study.netty.apidemo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;

/**
 * Channel 方法用例（常用 + 不常用）
 *
 * 本示例用 EmbeddedChannel（内嵌通道）演示：
 * 无需真实网络连接，直接在内存中跑 ChannelPipeline，学习 API 最方便。
 *
 * Channel 核心概念：
 *   - 一个 Channel 对应一个网络连接（或一个内嵌会话）
 *   - 数据进出都要经过 ChannelPipeline（责任链）
 *   - 所有操作都是异步的，返回 ChannelFuture / ChannelPromise
 */
public class ChannelApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== Channel 常用方法 ==========");

        // 创建一个内嵌通道（相当于一条内存中的"连接"）
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                // 透传：入站消息转发给下一个 handler
                ctx.fireChannelRead(msg);
            }
        });

        // ---- 写入与刷新（核心） ----
        channel.writeInbound("入站消息");               // 模拟"收到"数据（head 方向入站）
        Object in = channel.readInbound();             // 取出入站消息
        channel.writeOutbound("出站消息");              // 模拟"发送"数据（tail 方向出站）
        Object out = channel.readOutbound();           // 取出出站消息
        System.out.println("  入站: " + in + "，出站: " + out);

        // writeInbound 数据可以是 ByteBuf
        channel.writeInbound(Unpooled.copiedBuffer("字节数据", CharsetUtil.UTF_8));
        ByteBuf byteBuf = channel.readInbound();
        System.out.println("  ByteBuf 入站: " + byteBuf.toString(CharsetUtil.UTF_8));

        // ---- 状态查询 ----
        System.out.println("  isOpen=" + channel.isOpen()
                + ", isActive=" + channel.isActive()      // 已连接且未关闭
                + ", isRegistered=" + channel.isRegistered());
        System.out.println("  localAddress=" + channel.localAddress()
                + ", remoteAddress=" + channel.remoteAddress());

        // ---- 组件访问 ----
        channel.pipeline();       // 管道（handler 责任链）
        channel.eventLoop();      // 该 channel 绑定的 EventLoop（单线程执行模型）
        channel.alloc();          // 字节分配器（推荐用它创建 ByteBuf，自动池化）
        channel.config();         // 通道配置（写缓冲水位、自动读等）
        System.out.println("  pipeline/eventLoop/alloc/config 均已获取");

        // ---- 可写状态（高水位/低水位，背压控制） ----
        channel.isWritable();                 // 写缓冲是否低于高水位
        channel.bytesBeforeUnwritable();      // 距离高水位还有多少字节
        channel.bytesBeforeWritable();        // 距离低水位还有多少字节
        System.out.println("  isWritable=" + channel.isWritable()
                + ", bytesBeforeUnwritable=" + channel.bytesBeforeUnwritable());

        System.out.println();
        System.out.println("========== Channel 不常用但有用的方法 ==========");

        // ---- 写与刷分离（批量发送优化） ----
        channel.write("消息1");   // 只入队不发送（积攒）
        channel.write("消息2");
        channel.flush();          // 一次性刷出
        System.out.println("  write(不入队) + flush(刷出) 批量发送");

        // ---- 显式触发读取（自动读关闭时手动 read） ----
        channel.config().setAutoRead(false);  // 关闭自动读
        channel.read();                       // 手动触发一次读
        channel.config().setAutoRead(true);   // 恢复自动读
        System.out.println("  setAutoRead(false) 后手动 read() 控制流量");

        // ---- 生命周期 ----
        channel.isWritable();
        channel.close();          // 关闭连接（异步）
        channel.closeFuture();    // 连接关闭后完成的 Future
        System.out.println("  close() 后 isActive=" + channel.isActive()
                + ", isOpen=" + channel.isOpen());

        // ---- 其他 ----
        channel.id();             // 唯一标识
        channel.metadata();       // 通道元数据（类型、最大入站/出站消息大小）
        channel.parent();         // 父通道（ServerSocketChannel 的子连接返回服务端通道）
        channel.eventLoop().parent(); // EventLoopGroup
        channel.pipeline().firstContext();
        System.out.println("  id=" + channel.id() + "，metadata=" + channel.metadata());

        // ---- 内嵌通道专有方法 ----
        channel.runPendingTasks();   // 执行排队的任务（内嵌通道无真实线程）
        channel.checkException();    // 检查 pipeline 中是否有未处理异常
        channel.releaseInbound();    // 释放所有入站消息
        channel.releaseOutbound();   // 释放所有出站消息
        channel.finish();            // 完成并关闭（true=有未读消息）
        System.out.println("  内嵌通道: runPendingTasks/checkException/finish 演示完毕");
    }
}
