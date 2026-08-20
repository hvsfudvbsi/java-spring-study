package com.study.netty.performance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压测客户端：开 N 个并发连接，每个连接做 M 次"发送一行 -> 读取回声"，
 * 统计总耗时与吞吐。所有连接先建立完毕再同步起跑，保证并发负载同时施加。
 */
public class EchoBenchmark {

    /** 压测结果。 */
    public static final class Result {
        public final int connections;
        public final int messagesPerConnection;
        public final int totalMessages;
        public final long elapsedMillis;

        Result(int connections, int messagesPerConnection, int totalMessages, long elapsedMillis) {
            this.connections = connections;
            this.messagesPerConnection = messagesPerConnection;
            this.totalMessages = totalMessages;
            this.elapsedMillis = elapsedMillis;
        }

        /** 吞吐：每秒完成的消息数。 */
        public double throughputPerSecond() {
            return totalMessages * 1000.0 / elapsedMillis;
        }
    }

    /**
     * 对指定回显服务执行压测。
     *
     * @param connections           并发连接数
     * @param messagesPerConnection 每个连接发送的消息数（request-response 模式）
     * @return 压测结果；成功回声数等于 totalMessages 说明全部往返成功
     */
    public static Result run(String host, int port, int connections, int messagesPerConnection)
            throws InterruptedException {
        int total = connections * messagesPerConnection;
        ExecutorService pool = Executors.newFixedThreadPool(connections);
        CountDownLatch allConnected = new CountDownLatch(connections);
        CountDownLatch startGate = new CountDownLatch(1);   // 全部连上后统一起跑
        CountDownLatch allDone = new CountDownLatch(connections);
        AtomicLong echoed = new AtomicLong();

        long begin = System.nanoTime();
        for (int c = 0; c < connections; c++) {
            pool.submit(() -> {
                try (Socket socket = new Socket(host, port)) {
                    socket.setTcpNoDelay(true);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    OutputStream out = socket.getOutputStream();
                    allConnected.countDown();
                    startGate.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < messagesPerConnection; i++) {
                        out.write(("ping-" + i + "\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        String echo = in.readLine();
                        if (echo != null && echo.equals("ping-" + i)) {
                            echoed.incrementAndGet();
                        }
                    }
                } catch (IOException | InterruptedException ignored) {
                    // 连接失败或中断：该连接计数为 0
                } finally {
                    allDone.countDown();
                }
            });
        }

        allConnected.await(10, TimeUnit.SECONDS);
        startGate.countDown(); // 所有连接就绪，同时开压
        allDone.await(120, TimeUnit.SECONDS);
        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;
        pool.shutdownNow();

        return new Result(connections, messagesPerConnection, (int) echoed.get(), elapsedMillis);
    }
}
