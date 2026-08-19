package com.study.cloud.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务（微服务 B）
 *
 * 演示微服务间调用：
 *   order-service 通过 Feign 按服务名 "user-service" 调用用户服务，
 *   实际地址由 Eureka 注册中心发现 + LoadBalancer 负载均衡决定。
 *
 * 启动：mvn spring-boot:run -pl module-10-spring-cloud-microservices/order-service
 */
@EnableFeignClients
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
