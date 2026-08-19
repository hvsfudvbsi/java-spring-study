package com.study.cloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关（Spring Cloud Gateway）
 *
 * 网关作用（微服务架构的统一门户）：
 *   1. 统一入口：客户端只访问网关，不直接访问各微服务
 *   2. 路由转发：按路径把请求转发到对应服务（lb:// 负载均衡）
 *   3. 横切能力：鉴权、限流、日志、跨域（可在此统一实现）
 *
 * 路由配置见 application.yml：
 *   /api/user/**  -> user-service
 *   /api/order/** -> order-service
 *
 * 启动：mvn spring-boot:run -pl module-10-spring-cloud-microservices/api-gateway
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
