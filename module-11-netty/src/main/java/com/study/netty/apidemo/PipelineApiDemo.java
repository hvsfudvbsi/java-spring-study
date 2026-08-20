package com.study.netty.apidemo;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * ChannelPipeline / ChannelHandlerContext 方法用例（常用 + 不常用）
 *
 * Pipeline 责任链模型（面试必问）：
 *   ChannelPipeline 是双向链表：
 *     head <-> handler1 <-> handler2 <-> ... <-> tail
 *
 *   入站（读）流程：head -> ... -> tail   （ChannelInboundHandler 依次处理）
 *   出站（写）流程：tail -> ... -> head   （ChannelOutboundHandler 依次处理）
 *
 *   所以：解码器（入站）加在靠前，编码器（出站）加在靠后（先处理）。
 */
public class PipelineApiDemo {

    static class InHandlerA extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println("    InHandlerA 处理: " + msg);
            ctx.fireChannelRead(msg); // 传给下一个入站 handler
        }
    }

    static class InHandlerB extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println("    InHandlerB 处理: " + msg);
            ctx.fireChannelRead(msg);
        }
    }

    static class OutHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg,
                          io.netty.channel.ChannelPromise promise) {
            System.out.println("    OutHandler 出站: " + msg);
            ctx.write(msg, promise);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== ChannelPipeline 常用方法 ==========");
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelPipeline p = channel.pipeline();

        // ---- addLast / addFirst：追加/前置 handler ----
        p.addLast("handlerA", new InHandlerA());   // 带名字添加
        p.addLast("handlerB", new InHandlerB());
        p.addFirst("outHandler", new OutHandler()); // 出站 handler 放最前（出站先处理）
        System.out.println("  addLast/addFirst 后 pipeline: " + p.names());

        // ---- addBefore / addAfter：指定位置插入 ----
        p.addBefore("handlerB", "handlerA2", new InHandlerA());
        p.addAfter("handlerA", "handlerB2", new InHandlerB());
        System.out.println("  addBefore/addAfter 后 pipeline: " + p.names());

        // ---- fireChannelRead：从当前 handler 位置手动触发入站传播 ----
        p.fireChannelRead("手工触发消息");
        System.out.println("  fireChannelRead 手动触发入站传播");

        // ---- context：获取某个 handler 的上下文 ----
        ChannelHandlerContext ctxA = p.context("handlerA");
        System.out.println("  context(\"handlerA\") = " + ctxA.name()
                + "，其 handler = " + ctxA.handler().getClass().getSimpleName());

        // ---- first / last ----
        System.out.println("  first=" + p.first().getClass().getSimpleName()
                + ", last=" + p.last().getClass().getSimpleName());

        System.out.println();
        System.out.println("========== ChannelPipeline 不常用但有用的方法 ==========");

        // ---- remove：移除 handler（动态调整管道） ----
        p.remove("handlerA2");                    // 按名字移除
        p.remove(InHandlerB.class);               // 按类型移除（多个同名类移除第一个）
        ChannelInboundHandlerAdapter lastA = new InHandlerA();
        p.addLast("temp", lastA);
        p.remove(lastA);                          // 按实例移除
        System.out.println("  remove 后 pipeline: " + p.names());

        // ---- replace：替换仍然存在的 handlerB ----
        // 前面的 remove(InHandlerB.class) 已经移除了第一个同类型 Handler（handlerB2），
        // 因此这里必须替换当前仍在 Pipeline 中的 handlerB。
        p.replace("handlerB", "handlerB3", new InHandlerB());
        System.out.println("  replace 后 pipeline: " + p.names());

        // ---- 遍历查询 ----
        p.names();            // 所有 handler 名字
        p.toMap();            // name -> handler 的 Map
        p.firstContext();     // 第一个 handler 的上下文
        p.lastContext();      // 最后一个 handler 的上下文
        p.channel();          // 所属 channel
        System.out.println("  names=" + p.names() + "，toMap=" + p.toMap().keySet());

        // ---- 手动传播各类事件 ----
        p.fireChannelActive();              // 手动触发 channelActive
        p.fireChannelInactive();            // 手动触发 channelInactive
        p.fireChannelReadComplete();        // 手动触发读完成
        try {
            // 没有 Handler 消费异常时，EmbeddedChannel 会把异常重新抛给调用方；
            // 真实服务必须在 exceptionCaught 中记录、关闭连接或转交统一异常处理器。
            p.fireExceptionCaught(new RuntimeException("演示异常传播"));
        } catch (RuntimeException exception) {
            System.out.println("  fireExceptionCaught 未被业务 Handler 消费，调用方捕获: "
                    + exception.getMessage());
        }
        p.fireUserEventTriggered("自定义事件"); // 心跳等自定义事件走这里
        System.out.println("  fire* 系列：手动传播生命周期/异常/自定义事件");

        try {
            // EmbeddedChannel 会暂存未处理异常；显式 checkException 才能看到它。
            channel.checkException();
        } catch (RuntimeException exception) {
            System.out.println("  EmbeddedChannel.checkException 发现异常: " + exception.getMessage());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
