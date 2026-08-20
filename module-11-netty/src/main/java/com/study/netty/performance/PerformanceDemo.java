package com.study.netty.performance;

import io.netty.channel.Channel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 实操示例：Netty 性能演示（两部分）
 *
 * 第一部分 高并发连接：Netty 用固定少量 IO 线程（boss+worker）扛住数千并发连接，
 *           验证"线程数与连接数无关"（对比阻塞模型每连接一线程）。
 * 第二部分 压测对比：同样的按行回显协议，阻塞 IO（thread-per-connection）vs Netty，
 *           分别在低并发和高并发两档负载下统计线程数、耗时与吞吐，
 *           展示"并发升高后线程模型成为瓶颈、Netty 反超"的拐点。
 *
 * 运行：
 *   mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.performance.PerformanceDemo
 *
 * 参数（均可选）：
 *   -Dperf.connectCount=3000  第一部分保持的并发连接数
 *   -Dperf.workers=4          Netty worker 线程数
 *   -Dbench.connections=100   低并发档连接数   -Dbench.messages=300   低并发档每连接消息数
 *   -Dbench2.connections=3000 高并发档连接数   -Dbench2.messages=50    高并发档每连接消息数
 *
 * 注意：本机回环（loopback）压测数字只用于教学对比，不代表真实网络下的生产性能。
 */
public class PerformanceDemo {

    private static final int CONNECT_COUNT = Integer.getInteger("perf.connectCount", 3000);
    private static final int NETTY_WORKERS = Integer.getInteger("perf.workers", 4);

    private static final int LOW_CONNECTIONS = Integer.getInteger("bench.connections", 100);
    private static final int LOW_MESSAGES = Integer.getInteger("bench.messages", 300);
    private static final int HIGH_CONNECTIONS = Integer.getInteger("bench2.connections", 3000);
    private static final int HIGH_MESSAGES = Integer.getInteger("bench2.messages", 50);

    public static void main(String[] args) throws Exception {
        System.out.println("======== Netty 性能演示 ========");
        System.out.println("注意：本机回环压测数字仅用于教学对比，不代表生产性能。\n");

        highConcurrencyDemo();
        benchmarkLevel("低并发档（线程模型开销小）",
                LOW_CONNECTIONS, LOW_MESSAGES);
        benchmarkLevel("高并发档（线程上下文切换成为瓶颈）",
                HIGH_CONNECTIONS, HIGH_MESSAGES);
        printConclusion();
    }

    /** 第一部分：高并发连接。 */
    private static void highConcurrencyDemo() throws Exception {
        System.out.println("【第一部分】高并发连接：Netty 用少量线程扛大量连接");
        int port = freeTcpPort();
        Channel server = NettyEchoServer.start(port, NETTY_WORKERS);
        List<Socket> held = new ArrayList<>();
        try {
            for (int i = 0; i < CONNECT_COUNT; i++) {
                held.add(new Socket("127.0.0.1", port));
            }
            int ioThreads = countThreadsWithPrefix("nioEventLoopGroup");
            System.out.println("  保持并发连接数: " + held.size());
            System.out.println("  Netty IO 线程数: " + ioThreads
                    + "（boss(1) + worker(" + NETTY_WORKERS + ")）");
            System.out.println("  结论: " + held.size() + " 个并发连接只需 " + ioThreads
                    + " 个线程轮流处理（NIO 多路复用）；");
            System.out.println("        阻塞模型每连接一线程则需要约 " + held.size()
                    + " 个线程（每个约 1MB 栈空间）。");

            // 抽查连接仍然可用
            Socket probe = held.get(0);
            OutputStream out = probe.getOutputStream();
            out.write("alive\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("  抽查第 1 个连接写入 'alive' 无异常，连接仍可收发");
        } finally {
            for (Socket socket : held) {
                socket.close();
            }
            server.close().syncUninterruptibly();
        }
        System.out.println();
    }

    /** 第二部分：在指定负载档位下对比两种服务端实现。 */
    private static void benchmarkLevel(String label, int connections, int messages) throws Exception {
        System.out.println("【第二部分】压测对比: " + label);
        System.out.println("  负载: " + connections + " 个并发连接 × 每个 " + messages
                + " 条请求/响应 = " + (connections * messages) + " 条消息");

        // 1. 阻塞 IO（每连接一线程）
        int blockPort = freeTcpPort();
        ServerSocket blocking = BlockingEchoServer.start(blockPort);
        EchoBenchmark.Result blockResult;
        int blockThreads;
        try {
            blockResult = EchoBenchmark.run("127.0.0.1", blockPort, connections, messages);
            blockThreads = BlockingEchoServer.peakThreads(); // 峰值线程数（连接关闭后仍可读）
        } finally {
            blocking.close();
        }

        // 2. Netty（固定少量 worker）
        int nettyPort = freeTcpPort();
        Channel netty = NettyEchoServer.start(nettyPort, NETTY_WORKERS);
        EchoBenchmark.Result nettyResult;
        try {
            nettyResult = EchoBenchmark.run("127.0.0.1", nettyPort, connections, messages);
        } finally {
            netty.close().syncUninterruptibly();
        }

        System.out.println("  服务端实现             | 连接数 | 消息总数 | 线程数 | 耗时    | 吞吐(msg/s)");
        System.out.println("  -----------------------|--------|----------|--------|---------|------------");
        System.out.printf("  阻塞 IO（每连接一线程） | %6d | %8d | %6d | %7dms | %12.0f%n",
                blockResult.connections, blockResult.totalMessages, blockThreads,
                blockResult.elapsedMillis, blockResult.throughputPerSecond());
        System.out.printf("  Netty（%d worker）    | %6d | %8d | %6d | %7dms | %12.0f%n",
                NETTY_WORKERS, nettyResult.connections, nettyResult.totalMessages, 1 + NETTY_WORKERS,
                nettyResult.elapsedMillis, nettyResult.throughputPerSecond());
        System.out.println();
    }

    private static void printConclusion() {
        System.out.println("  结论（并发越高，Netty 优势越明显）:");
        System.out.println("  1. 低并发时阻塞模型线程开销小、回环上甚至更快；Netty 有事件分发与分配开销；");
        System.out.println("  2. 并发升高后，阻塞模型线程数 = 连接数，上下文切换/调度成为瓶颈，Netty 反超；");
        System.out.println("  3. 更本质的差距在资源可扩展性：3000 连接对阻塞模型是约 3000 个线程"
                + "（约 3GB 栈内存），对 Netty 只是 5 个固定线程；");
        System.out.println("  4. 回环数据走内核内存，吞吐远高于真实网络，数字仅用于对比教学。");
    }

    /** 找一个空闲 TCP 端口。 */
    private static int freeTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 统计存活线程中名字带指定前缀的数量（Netty IO 线程以 nioEventLoopGroup 开头）。 */
    private static int countThreadsWithPrefix(String prefix) {
        return (int) Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith(prefix))
                .count();
    }
}
