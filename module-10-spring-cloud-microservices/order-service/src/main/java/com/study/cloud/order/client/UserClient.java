package com.study.cloud.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 服务间调用客户端：Feign 调用 user-service
 *
 * 关键点：
 *   @FeignClient(name = "user-service")  —— name 是注册中心里的服务名
 *   不写 url：地址由 Eureka 服务发现 + LoadBalancer 从多个实例中选择
 *
 * 对比 module-08 的 @FeignClient(url = "http://localhost:8080")：
 *   - 直连 url：写死地址，仅适合学习/外部固定服务
 *   - 按服务名：动态发现 + 负载均衡，这才是微服务正确的调用方式
 */
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    Map<String, Object> getUser(@PathVariable("id") Long id);
}
