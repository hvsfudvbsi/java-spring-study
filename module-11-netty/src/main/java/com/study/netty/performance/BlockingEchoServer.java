package com.study.netty.performance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能对比用：阻塞 IO 回显服务（thread-per-connection 模型）。
 *
 * 与 NettyEchoServer 提供相同的按行回显协议，但每个连接独占一个线程，
 * 线程数与连接数成正比——这是对比 Netty EventLoop 少线程模型的对照组。
 * 仅用于教学演示，不作为生产实现。
 */
public class BlockingEchoServer {

    private static final AtomicInteger ACTIVE_THREADS = new AtomicInteger();
    /** 峰值并发线程数（最大同时处理的连接数），压测结束后仍可读取。 */
    private static final AtomicInteger PEAK_THREADS = new AtomicInteger();

    /** 启动阻塞回显服务（每连接一线程），返回 ServerSocket；调用方负责关闭。 */
    public static ServerSocket start(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        Thread acceptor = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    // thread-per-connection：每个连接一个线程，连接数 = 线程数。
                    Thread worker = new Thread(() -> handle(socket), "blocking-conn");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        }, "blocking-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        return serverSocket;
    }

    /** 单连接处理：按行读取，原样回写（带换行符，与 Netty 版协议一致）。 */
    private static void handle(Socket socket) {
        int current = ACTIVE_THREADS.incrementAndGet();
        PEAK_THREADS.accumulateAndGet(current, Math::max); // 记录峰值并发线程数
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {
            String line;
            while ((line = in.readLine()) != null) {
                out.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException ignored) {
            // 客户端断开是正常现象
        } finally {
            ACTIVE_THREADS.decrementAndGet();
        }
    }

    /** 本次运行中的峰值连接线程数（连接关闭后仍可读取，用于对比线程数与连接数的关系）。 */
    public static int peakThreads() {
        return PEAK_THREADS.get();
    }
}
