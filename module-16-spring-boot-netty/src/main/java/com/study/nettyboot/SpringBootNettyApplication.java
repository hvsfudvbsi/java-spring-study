package com.study.nettyboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot + Netty 集成示例入口。
 *
 * 一个进程里同时运行：
 *   - 内嵌 Tomcat（默认 8080）：HTTP/REST，见 WebController
 *   - Netty TCP 服务（默认 19090）：长连接/高吞吐流量，见 NettyTcpServer
 *
 * 运行：mvn spring-boot:run -pl module-16-spring-boot-netty
 */
@SpringBootApplication
public class SpringBootNettyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootNettyApplication.class, args);
    }
}
