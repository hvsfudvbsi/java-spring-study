package com.study.cloud.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务（微服务 A）
 *
 * 独立部署的 Spring Boot 应用，启动时自动注册到 Eureka 注册中心。
 * 提供用户查询接口，供其他服务（order-service）通过服务发现调用。
 *
 * 启动：mvn spring-boot:run -pl module-10-spring-cloud-microservices/user-service
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
