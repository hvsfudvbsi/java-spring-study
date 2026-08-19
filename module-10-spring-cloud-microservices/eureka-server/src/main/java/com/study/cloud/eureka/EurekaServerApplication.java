package com.study.cloud.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * 服务注册中心（Eureka Server）
 *
 * 作用（微服务架构核心组件）：
 *   所有服务启动时向注册中心"报到"，下线时自动注销。
 *   服务消费者通过注册中心发现服务实例的地址（服务发现），
 *   配合 LoadBalancer 实现负载均衡。
 *
 * 访问管理界面：http://localhost:8761
 *   可以看到所有注册上来的服务实例、健康状态、副本数。
 *
 * 启动：mvn spring-boot:run -pl module-10-spring-cloud-microservices/eureka-server
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
