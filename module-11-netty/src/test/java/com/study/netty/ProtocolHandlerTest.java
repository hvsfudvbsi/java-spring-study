package com.study.netty;

import com.study.netty.chat.ChatServerHandler;
import com.study.netty.heartbeat.HeartbeatServer;
import com.study.netty.http.HttpServerHandler;
import com.study.netty.ssl.SslServer;
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Netty 协议处理器的纯单元测试：使用 EmbeddedChannel，不启动真实端口。
 */
class ProtocolHandlerTest {

    @Test
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
    void websocketHandlerShouldEchoTextFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler());

        channel.writeInbound(new TextWebSocketFrame("hello websocket"));

        TextWebSocketFrame response = channel.readOutbound();
        assertEquals("echo: hello websocket", response.text());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void websocketHandlerShouldRespondPongToPingFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameHandler());

        channel.writeInbound(new PingWebSocketFrame(Unpooled.copiedBuffer("heartbeat", CharsetUtil.UTF_8)));

        PongWebSocketFrame response = channel.readOutbound();
        assertEquals("heartbeat", response.content().toString(CharsetUtil.UTF_8));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void sslContextShouldBuildClientConfiguration() throws Exception {
        SslContext context = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        assertTrue(context.isClient());
    }

    @Test
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
