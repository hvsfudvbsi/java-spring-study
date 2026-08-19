package com.study.cloud.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * OpenFeign 客户端示例二：调用本模块内置的"模拟远程服务"
 * （MockRemoteController 扮演另一个微服务）
 *
 * 模拟微服务调用链路：
 *   客户端 -> [Feign 远程调用] -> 模拟远程服务（随机失败/变慢）
 *              -> [Resilience4j 熔断] -> 失败时降级
 */
@FeignClient(name = "mock-remote-service", url = "http://localhost:${server.port}")
public interface RemoteServiceClient {

    @GetMapping("/api/mock/hello")
    Map<String, Object> callHello(@RequestParam("name") String name);
}
