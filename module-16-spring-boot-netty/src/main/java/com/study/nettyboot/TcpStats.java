package com.study.nettyboot;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty TCP 服务的共享统计 Bean。
 *
 * 由 Spring 管理，Netty 处理器写入、Tomcat 的 REST 接口读取——
 * 这就是"同 JVM 嵌入 Netty"的核心好处：Netty 与业务层共享 Bean/数据库/事务，
 * 无需跨进程通信就能拿到运行状态。
 */
@Component
public class TcpStats {

    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicInteger activeConnections = new AtomicInteger();

    /** 新连接建立时调用。 */
    public void onConnect() {
        activeConnections.incrementAndGet();
    }

    /** 连接断开时调用。 */
    public void onDisconnect() {
        activeConnections.decrementAndGet();
    }

    /** 每收到一条业务消息时调用。 */
    public void onMessage() {
        totalMessages.incrementAndGet();
    }

    /** 累计处理的消息数。 */
    public long totalMessages() {
        return totalMessages.get();
    }

    /** 当前在线连接数。 */
    public int activeConnections() {
        return activeConnections.get();
    }
}
