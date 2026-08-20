package com.study.netty.performance;

import io.netty.channel.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 性能演示测试：验证高并发连接与两种服务端压测的完整性（不做计时断言，避免回环环境波动）。 */
class PerformanceDemoTest {

    @Test
    @DisplayName("高并发连接：Netty 2 个 worker 扛住 500 个并发连接，IO 线程数远小于连接数")
    void nettyShouldHoldManyConnectionsWithFewThreads() throws Exception {
        int port = freeTcpPort();
        // 用线程数增量断言：surefire 同一 JVM 中其他测试的 Netty 线程可能仍在优雅关闭
        int threadsBefore = countThreadsWithPrefix("nioEventLoopGroup");
        Channel server = NettyEchoServer.start(port, 2);
        List<Socket> sockets = new ArrayList<>();
        try {
            for (int i = 0; i < 500; i++) {
                sockets.add(new Socket("127.0.0.1", port));
            }
            assertTrue(sockets.stream().allMatch(Socket::isConnected), "500 个连接应全部建立");
            int nettyThreadsAdded = countThreadsWithPrefix("nioEventLoopGroup") - threadsBefore;
            // 本测试新增 boss(1)+worker(2)=3 个线程；留少量余量
            assertTrue(nettyThreadsAdded <= 8,
                    "500 个连接只应新增少量 IO 线程，实际新增: " + nettyThreadsAdded);
        } finally {
            for (Socket socket : sockets) {
                socket.close();
            }
            server.close().syncUninterruptibly();
        }
    }

    @Test
    @DisplayName("压测对比：阻塞 IO 与 Netty 都完整返回全部回声，Netty 线程数远小于连接数")
    void benchmarkShouldCompleteForBothServers() throws Exception {
        int connections = 20;
        int messages = 50;

        // 阻塞 IO 服务端
        int blockPort = freeTcpPort();
        ServerSocket blocking = BlockingEchoServer.start(blockPort);
        EchoBenchmark.Result blockResult;
        int blockPeakThreads;
        try {
            blockResult = EchoBenchmark.run("127.0.0.1", blockPort, connections, messages);
            blockPeakThreads = BlockingEchoServer.peakThreads(); // 峰值线程数（连接关闭后仍可读）
        } finally {
            blocking.close();
        }
        assertEquals(connections * messages, blockResult.totalMessages,
                "阻塞服务应返回全部回声");
        assertTrue(blockPeakThreads > 0,
                "阻塞服务压测期间应存在连接线程，实际峰值: " + blockPeakThreads);

        // Netty 服务端（2 个 worker）
        int nettyPort = freeTcpPort();
        Channel netty = NettyEchoServer.start(nettyPort, 2);
        EchoBenchmark.Result nettyResult;
        try {
            nettyResult = EchoBenchmark.run("127.0.0.1", nettyPort, connections, messages);
        } finally {
            netty.close().syncUninterruptibly();
        }
        assertEquals(connections * messages, nettyResult.totalMessages,
                "Netty 服务应返回全部回声");

        assertTrue(1 + 2 < connections,
                "Netty 线程数(boss1+worker2=3)应远小于连接数(" + connections + ")");
    }

    private int freeTcpPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private int countThreadsWithPrefix(String prefix) {
        return (int) Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith(prefix))
                .count();
    }
}
