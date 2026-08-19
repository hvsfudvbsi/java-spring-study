package com.study.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Spring Cloud 学习模块入口
 *
 * 本模块演示微服务三大基础能力（无需注册中心，开箱即用）：
 *   1. OpenFeign：声明式 HTTP 调用（像调用本地方法一样调用远程服务）
 *   2. LoadBalancer：客户端负载均衡
 *   3. Resilience4j：熔断、降级、限流
 *
 * 进阶（注册中心/配置中心/网关）见 README 的"微服务全家桶"章节：
 *   Eureka / Nacos 服务注册发现
 *   Spring Cloud Config / Nacos 配置中心
 *   Spring Cloud Gateway 网关
 */
@EnableFeignClients // 扫描 @FeignClient 接口并生成代理
@SpringBootApplication
public class SpringCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudApplication.class, args);
    }
}
