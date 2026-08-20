package com.study.netty;

import com.study.netty.chat.ChatServerHandler;
import com.study.netty.heartbeat.HeartbeatServer;
import com.study.netty.http.HttpServerHandler;
import com.study.netty.udp.UdpServer;
import com.study.netty.websocket.WebSocketFrameHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Netty 协议处理器的纯单元测试：使用 EmbeddedChannel，不启动真实端口。
 */
class ProtocolHandlerTest {

    @Test
    @DisplayName("HTTP /hello 返回 200 与 Hello 正文")
    void httpHandlerShouldReturnRouteResponse() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerHandler());
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);

        channel.writeInbound(request);

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        assertEquals("Hello from Netty HTTP", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("HTTP /health 返回 200 与 JSON 状态")
    void httpHandlerShouldReturnHealthJson() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerHandler());
        channel.writeInbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/health"));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        assertEquals("{\"status\":\"UP\"}", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("HTTP 带查询字符串仍按路径路由（? 之后被忽略）")
    void httpHandlerShouldIgnoreQueryString() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerHandler());
        channel.writeInbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello?name=alice"));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        assertEquals("Hello from Netty HTTP", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("HTTP 未知路由返回 404 与路径提示")
    void httpHandlerShouldReturnNotFoundForUnknownRoute() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerHandler());
        channel.writeInbound(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/missing"));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(404, response.status().code());
        assertEquals("Not Found: /missing", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("HTTP 非 Keep-Alive 请求：响应不设长连接头，发送后关闭连接")
    void httpHandlerShouldCloseOnNonKeepAlive() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerHandler());
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        channel.writeInbound(request);

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        assertNull(response.headers().get(HttpHeaderNames.CONNECTION),
                "非 Keep-Alive 响应不应带 Connection: keep-alive");
        response.release();

        // writeAndFlush 完成后的 listener 会关闭连接；runPendingTasks 执行该回调
        channel.runPendingTasks();
        assertFalse(channel.isOpen(), "非 Keep-Alive 响应发送后连接应被关闭");
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("UDP 处理器把数据报回显给发送方地址")
    void udpHandlerShouldEchoDatagramToSender() {
        EmbeddedChannel channel = new EmbeddedChannel(new UdpServer.UdpServerHandler());
        InetSocketAddress sender = new InetSocketAddress("127.0.0.1", 19000);
        InetSocketAddress server = new InetSocketAddress("127.0.0.1", 18084);
        DatagramPacket packet = new DatagramPacket(
                Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8), server, sender);

        channel.writeInbound(packet);

        DatagramPacket response = channel.readOutbound();
        assertEquals(sender, response.recipient());
        assertEquals("UDP echo: hello", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("WebSocket 文本帧回显（echo: 前缀）")
    void websocketHandlerShouldEchoTextFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler());

        channel.writeInbound(new TextWebSocketFrame("hello websocket"));

        TextWebSocketFrame response = channel.readOutbound();
        assertEquals("echo: hello websocket", response.text());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("WebSocket 二进制帧回显相同内容")
    void websocketHandlerShouldEchoBinaryFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler());

        channel.writeInbound(new BinaryWebSocketFrame(
                Unpooled.copiedBuffer("raw-bytes", CharsetUtil.UTF_8)));

        BinaryWebSocketFrame response = channel.readOutbound();
        assertEquals("raw-bytes", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("WebSocket Ping 帧回复 Pong 帧（协议层心跳）")
    void websocketHandlerShouldRespondPongToPingFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler());

        channel.writeInbound(new PingWebSocketFrame(Unpooled.copiedBuffer("heartbeat", CharsetUtil.UTF_8)));

        PongWebSocketFrame response = channel.readOutbound();
        assertEquals("heartbeat", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("WebSocket 未支持帧返回可读错误提示")
    void websocketHandlerShouldRejectUnsupportedFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler());

        // 未被业务处理的帧类型（如 Pong 帧主动发送、Continuation 等）返回提示文本
        channel.writeInbound(new PongWebSocketFrame(Unpooled.copiedBuffer("x", CharsetUtil.UTF_8)));

        TextWebSocketFrame response = channel.readOutbound();
        assertEquals("unsupported frame: PongWebSocketFrame", response.text());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("TLS 客户端 SslContext 按不校验证书模式构建成功")
    void sslContextShouldBuildClientConfiguration() throws Exception {
        SslContext context = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        assertTrue(context.isClient());
    }

    @Test
    @DisplayName("心跳服务端：收到 PING 回 PONG，读空闲判定假死并关闭")
    void heartbeatHandlerShouldRespondToPingAndCloseOnReaderIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new HeartbeatServer.HeartbeatServerHandler());

        channel.writeInbound(Unpooled.copiedBuffer("PING", CharsetUtil.UTF_8));
        ByteBuf response = channel.readOutbound();
        assertEquals("PONG", response.toString(CharsetUtil.UTF_8));
        response.release();

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        assertFalse(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("心跳服务端：普通数据不回 PONG，仅 PING 触发响应")
    void heartbeatHandlerShouldNotRespondToPlainData() {
        EmbeddedChannel channel = new EmbeddedChannel(new HeartbeatServer.HeartbeatServerHandler());

        channel.writeInbound(Unpooled.copiedBuffer("HELLO", CharsetUtil.UTF_8));
        assertNull(channel.readOutbound(), "非 PING 数据不应触发响应");
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("心跳服务端：写空闲不判定假死，只有读空闲才关闭连接")
    void heartbeatHandlerShouldNotCloseOnWriterIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new HeartbeatServer.HeartbeatServerHandler());

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);

        assertTrue(channel.isOpen(), "写空闲不代表客户端假死，不应关闭连接");
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("群聊：NICK 改名 + 消息广播给其他在线成员")
    void chatHandlerShouldRenameAndBroadcastMessage() {
        DefaultChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        EmbeddedChannel first = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        drainOutbound(first);
        EmbeddedChannel second = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        assertEquals(2, channels.size());
        runPendingTasks(first, second);
        drainOutbound(first);
        drainOutbound(second);

        first.writeInbound("NICK:Alice");
        assertEquals("[系统] 昵称已改为: Alice", first.<String>readOutbound());
        assertEquals("[系统] 用户-未知 改名为 Alice", readOutboundEventually(second));

        first.writeInbound("hello");

        assertEquals("Alice: hello", readOutboundEventually(second));
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    @Test
    @DisplayName("群聊：quit 退出时本人收到再见、广播离线并关闭连接")
    void chatHandlerShouldBroadcastQuit() {
        DefaultChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        EmbeddedChannel first = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        drainOutbound(first);
        EmbeddedChannel second = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        runPendingTasks(first, second);
        drainOutbound(first);
        drainOutbound(second);

        first.writeInbound("NICK:Alice");
        assertEquals("[系统] 昵称已改为: Alice", first.<String>readOutbound());
        assertEquals("[系统] 用户-未知 改名为 Alice", readOutboundEventually(second));

        first.writeInbound("quit");
        assertEquals("[系统] 再见 Alice！", first.<String>readOutbound());
        // ctx.close() 触发 channelInactive -> 广播离线
        assertEquals("[系统] Alice 离开了聊天室（当前在线 1 人）", readOutboundEventually(second));
        assertFalse(first.isOpen(), "quit 后连接应被关闭");
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    @Test
    @DisplayName("群聊：@昵称 私聊只发给指定用户，其他用户收不到")
    void chatHandlerShouldSendPrivateMessageOnlyToTarget() {
        DefaultChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        EmbeddedChannel alice = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        drainOutbound(alice);
        EmbeddedChannel bob = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        EmbeddedChannel carol = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        assertEquals(3, channels.size());
        runPendingTasks(alice, bob, carol);
        drainOutbound(alice);
        drainOutbound(bob);
        drainOutbound(carol);

        // 三个用户都设置昵称
        alice.writeInbound("NICK:Alice");
        assertEquals("[系统] 昵称已改为: Alice", alice.<String>readOutbound());
        readOutboundEventually(bob); // 改名广播
        readOutboundEventually(carol);
        bob.writeInbound("NICK:Bob");
        assertEquals("[系统] 昵称已改为: Bob", bob.<String>readOutbound());
        readOutboundEventually(alice);
        readOutboundEventually(carol);
        carol.writeInbound("NICK:Carol");
        assertEquals("[系统] 昵称已改为: Carol", carol.<String>readOutbound());
        readOutboundEventually(alice);
        readOutboundEventually(bob);

        // Alice 私聊 Bob
        alice.writeInbound("@Bob 你好，这是私聊");

        // 只有 Bob 收到私聊，Carol 收不到
        assertEquals("[私聊] Alice: 你好，这是私聊", readOutboundEventually(bob));
        assertNull(readOutboundEventually(carol), "私聊不应广播给其他用户");
        // 发送者 Alice 收到送达回执
        assertEquals("[私聊→Bob] 已送达", readOutboundEventually(alice));

        alice.finishAndReleaseAll();
        bob.finishAndReleaseAll();
        carol.finishAndReleaseAll();
    }

    @Test
    @DisplayName("群聊：私聊不存在的用户提示不在线")
    void chatHandlerShouldReportOfflinePrivateTarget() {
        DefaultChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        EmbeddedChannel alice = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        drainOutbound(alice);
        alice.writeInbound("NICK:Alice");
        assertEquals("[系统] 昵称已改为: Alice", alice.<String>readOutbound());

        alice.writeInbound("@Nobody 有人吗");

        assertEquals("[系统] 用户 Nobody 不在线", readOutboundEventually(alice));
        alice.finishAndReleaseAll();
    }

    @Test
    @DisplayName("群聊：私聊格式错误（@ 后无内容）返回格式提示")
    void chatHandlerShouldRejectMalformedPrivateMessage() {
        DefaultChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        EmbeddedChannel alice = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        drainOutbound(alice);
        alice.writeInbound("NICK:Alice");
        assertEquals("[系统] 昵称已改为: Alice", alice.<String>readOutbound());

        alice.writeInbound("@");
        assertEquals("[系统] 私聊格式: @昵称 内容，例如 '@Alice 你好'", readOutboundEventually(alice));

        alice.writeInbound("@Bob");
        assertEquals("[系统] 私聊格式: @昵称 内容，例如 '@Alice 你好'", readOutboundEventually(alice));
        alice.finishAndReleaseAll();
    }

    @Test
    @DisplayName("群聊：连接断开（channelInactive）广播离线通知")
    void chatHandlerShouldBroadcastOnDisconnect() {
        DefaultChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        EmbeddedChannel first = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        drainOutbound(first);
        EmbeddedChannel second = new EmbeddedChannel(
                DefaultChannelId.newInstance(), new ChatServerHandler(channels));
        runPendingTasks(first, second);
        drainOutbound(first);
        drainOutbound(second);

        first.writeInbound("NICK:Alice");
        assertEquals("[系统] 昵称已改为: Alice", first.<String>readOutbound());
        assertEquals("[系统] 用户-未知 改名为 Alice", readOutboundEventually(second));

        // 直接关闭连接，触发 channelInactive
        first.close();
        assertEquals("[系统] Alice 离开了聊天室（当前在线 1 人）", readOutboundEventually(second));
        first.finishAndReleaseAll();
        second.finishAndReleaseAll();
    }

    private String readOutboundEventually(EmbeddedChannel channel) {
        for (int attempt = 0; attempt < 100; attempt++) {
            runPendingTasks(channel);
            String message = channel.readOutbound();
            if (message != null) {
                return message;
            }
            Thread.yield();
        }
        return null;
    }

    private void runPendingTasks(EmbeddedChannel... channels) {
        for (EmbeddedChannel channel : channels) {
            channel.runPendingTasks();
        }
    }

    private void drainOutbound(EmbeddedChannel channel) {
        while (channel.readOutbound() != null) {
            // EmbeddedChannel 中的 IM 文本消息不是引用计数对象。
        }
    }
}
