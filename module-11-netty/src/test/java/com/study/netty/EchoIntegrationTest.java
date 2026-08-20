package com.study.netty;

import com.study.netty.echo.EchoClient;
import com.study.netty.echo.EchoServer;
import io.netty.channel.Channel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回声集成测试：真实 TCP 连接，验证完整"发送 -> 服务端 -> 回声 -> 接收"链路
 *
 * 端口选择：用 ServerSocket(0) 找一个空闲端口，避免与其他测试冲突
 */
class EchoIntegrationTest {

    private static int port;
    private static Channel serverChannel;

    @BeforeAll
    static void startServer() throws Exception {
        // 找一个空闲端口
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        serverChannel = EchoServer.start(port);
    }

    @AfterAll
    static void stopServer() {
        serverChannel.close().syncUninterruptibly(); // 关闭后线程组自动优雅关闭
    }

    @Test
    @DisplayName("真实 TCP 回声链路：发送消息原样返回")
    void echoRoundTrip() throws Exception {
        String response = EchoClient.run("127.0.0.1", port, "集成测试消息");
        assertNotNull(response, "应收到回声");
        assertTrue(response.contains("集成测试消息"));
    }

    @Test
    @DisplayName("真实 TCP 回声链路：连续多次往返均能收到回声（每条消息依次回显）")
    void echoMultipleRoundTrips() throws Exception {
        for (int i = 1; i <= 3; i++) {
            String message = "第" + i + "次往返";
            String response = EchoClient.run("127.0.0.1", port, message);
            assertNotNull(response, "第 " + i + " 次应收到回声");
            assertTrue(response.contains(message));
        }
    }
}
