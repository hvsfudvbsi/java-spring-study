package com.study.netty;

import com.study.netty.ssl.SslClient;
import com.study.netty.ssl.SslServer;
import io.netty.channel.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TLS 示例测试：验证自签名证书 + SSL Pipeline 启动，以及真实 TLS 端到端回显。 */
class SslServerTest {

    @Test
    @DisplayName("TLS 服务端能在随机端口启动（自签名证书 + SSL Pipeline 创建成功）")
    void sslServerShouldStartOnRandomPort() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        Channel server = SslServer.start(port);
        try {
            assertTrue(server.isActive());
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    @Test
    @DisplayName("真实 TLS 链路：客户端握手后发送文本，收到服务端 TLS echo 回显")
    void sslRoundTripThroughRealSocket() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        Channel server = SslServer.start(port);
        try {
            String response = SslClient.connect("127.0.0.1", port, "TLS 端到端测试");
            assertEquals("TLS echo: TLS 端到端测试", response);
        } finally {
            server.close().syncUninterruptibly();
        }
    }
}
