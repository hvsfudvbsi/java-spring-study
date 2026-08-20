package com.study.netty;

import com.study.netty.http.HttpClient;
import com.study.netty.http.HttpServer;
import com.study.netty.udp.UdpClient;
import com.study.netty.udp.UdpServer;
import io.netty.channel.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 仅访问本机随机端口，验证 HTTP 和 UDP 的完整网络链路。 */
class NetworkProtocolIntegrationTest {

    @Test
    @DisplayName("真实 HTTP 链路：Netty 服务端 /health 返回 200 状态 JSON")
    void httpShouldRoundTripThroughNettyServer() throws Exception {
        int port = freeTcpPort();
        Channel server = HttpServer.start(port);
        try {
            assertEquals("200 {\"status\":\"UP\"}",
                    HttpClient.get("127.0.0.1", port, "/health"));
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    @Test
    @DisplayName("真实 UDP 链路：数据报经 Netty 服务端回显")
    void udpShouldRoundTripThroughNettyServer() throws Exception {
        int port = freeUdpPort();
        Channel server = UdpServer.start(port);
        try {
            assertEquals("UDP echo: hello udp",
                    UdpClient.send("127.0.0.1", port, "hello udp"));
        } finally {
            server.close().syncUninterruptibly();
        }
    }

    private int freeTcpPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
