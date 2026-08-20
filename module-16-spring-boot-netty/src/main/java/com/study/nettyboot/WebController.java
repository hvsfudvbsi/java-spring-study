package com.study.nettyboot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内嵌 Tomcat 的 REST 接口。
 *
 * /api/tcp-stats 直接读取 Netty TCP 服务的共享统计 Bean——
 * 同一 JVM 里 Tomcat 和 Netty 共享 Spring 状态，无需跨进程调用。
 */
@RestController
@RequestMapping("/api")
public class WebController {

    private final TcpStats stats;
    private final NettyTcpServer nettyServer;

    public WebController(TcpStats stats, NettyTcpServer nettyServer) {
        this.stats = stats;
        this.nettyServer = nettyServer;
    }

    /** 验证 HTTP 链路（Tomcat 端口）。 */
    @GetMapping("/hello")
    public String hello() {
        return "Hello from Tomcat";
    }

    /** 读取 Netty TCP 服务的运行状态（同一 JVM 共享 Bean）。 */
    @GetMapping("/tcp-stats")
    public Map<String, Object> tcpStats() {
        return Map.of(
                "nettyTcpPort", nettyServer.localPort(),
                "activeConnections", stats.activeConnections(),
                "totalMessages", stats.totalMessages());
    }
}
