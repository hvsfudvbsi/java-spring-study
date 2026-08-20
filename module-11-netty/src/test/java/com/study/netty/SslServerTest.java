package com.study.netty;

import com.study.netty.ssl.SslServer;
import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** TLS 示例启动测试：验证自签名证书和 SSL Pipeline 可以创建。 */
class SslServerTest {

    @Test
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
}
