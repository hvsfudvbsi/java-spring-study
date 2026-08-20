package com.study.nettyboot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试：同一个 Spring Boot 进程里 Tomcat（REST）与 Netty（TCP）同时工作，
 * 且共享统计 Bean（REST 能读到 Netty 的状态）。
 *
 * Tomcat 用随机端口；Netty 用 netty.server.port=0 绑定随机端口，
 * 通过 NettyTcpServer.localPort() 获取。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "netty.server.port=0")
class SpringBootNettyIntegrationTest {

    @LocalServerPort
    private int tomcatPort;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private NettyTcpServer nettyServer;

    @Autowired
    private TcpStats stats;

    @Test
    @DisplayName("Tomcat REST 链路：/api/hello 返回 200")
    void tomcatRestShouldRespond() {
        String body = rest.getForObject("/api/hello", String.class);
        assertEquals("Hello from Tomcat", body);
    }

    @Test
    @DisplayName("Netty TCP 链路：连接后收到欢迎，发 3 行收到 3 条回声")
    void nettyTcpShouldEcho() throws Exception {
        int nettyPort = nettyServer.localPort();
        try (Socket socket = new Socket("127.0.0.1", nettyPort)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String welcome = in.readLine();
            assertTrue(welcome.startsWith("welcome"), "应收到欢迎消息: " + welcome);

            for (int i = 1; i <= 3; i++) {
                out.write(("hello-" + i + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                assertEquals("echo: hello-" + i, in.readLine());
            }
        }
        // 连接关闭后在线数归零（channelInactive 在 Netty 线程异步执行，轮询等待）
        awaitValue(0, stats::activeConnections);
        assertEquals(3, stats.totalMessages());
    }

    /** 轮询等待某个指标到达期望值（异步事件在 Netty 线程执行，不能立即断言）。 */
    private static void awaitValue(int expected, java.util.function.IntSupplier actual)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (actual.getAsInt() == expected) {
                return;
            }
            Thread.sleep(20);
        }
        assertEquals(expected, actual.getAsInt(), "轮询超时后仍未达到期望值");
    }

    @Test
    @DisplayName("共享 Bean：Tomcat REST 能读到 Netty TCP 的实时统计")
    void restShouldReadNettyStats() throws Exception {
        int nettyPort = nettyServer.localPort();
        // 开一个 TCP 连接并发送一条消息
        try (Socket socket = new Socket("127.0.0.1", nettyPort)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            in.readLine(); // 欢迎消息
            OutputStream out = socket.getOutputStream();
            out.write("统计测试\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals("echo: 统计测试", in.readLine());

            // Tomcat REST 读取 Netty 状态：连接数 1、消息数至少 1
            @SuppressWarnings("unchecked")
            Map<String, Object> statsJson = rest.getForObject("/api/tcp-stats", Map.class);
            assertEquals(nettyPort, ((Number) statsJson.get("nettyTcpPort")).intValue());
            assertEquals(1, ((Number) statsJson.get("activeConnections")).intValue());
            assertTrue(((Number) statsJson.get("totalMessages")).intValue() >= 1);
        }
    }
}
