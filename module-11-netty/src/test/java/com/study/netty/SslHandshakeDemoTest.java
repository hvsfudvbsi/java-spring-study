package com.study.netty;

import com.study.netty.ssl.SslHandshakeDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** TLS 握手演示测试：真实握手成功并返回协商协议、密码套件与回显。 */
class SslHandshakeDemoTest {

    @Test
    @DisplayName("TLS 握手演示：协商出 TLS 协议与密码套件，并收到服务端回显")
    void handshakeDemoShouldNegotiateAndEcho() throws Exception {
        String summary = SslHandshakeDemo.runDemo();

        assertTrue(summary.contains("协议=TLS"),
                "应协商出 TLS 协议，实际: " + summary);
        assertTrue(summary.contains("密码套件="),
                "应打印密码套件，实际: " + summary);
        assertTrue(summary.contains("TLS echo: handshake-demo"),
                "应收到服务端回显，实际: " + summary);
    }
}
