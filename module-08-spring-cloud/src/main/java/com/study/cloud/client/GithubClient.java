package com.study.cloud.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * OpenFeign 客户端示例一：调用真实的 GitHub 公开 API
 *
 * 使用方式（无需注入 RestTemplate）：
 *   1. 定义接口 + @FeignClient
 *   2. 在需要的地方注入接口，直接调用方法
 *
 * @FeignClient 关键属性：
 *   name    服务名（有注册中心时按名字找服务；无注册中心 + url 时直接调 url）
 *   url     目标地址（学习阶段直连，跳过注册中心）
 *   fallback 降级实现类（需要 @EnableFeignClients 开启，见 RemoteServiceClient）
 *
 * Feign 优势 vs RestTemplate：
 *   - 声明式：接口即契约，自动序列化/反序列化
 *   - 可读性好：一眼看清调用了哪个服务的哪个接口
 */
@FeignClient(name = "github-api", url = "https://api.github.com")
public interface GithubClient {

    @GetMapping("/users/{username}")
    Map<String, Object> getUser(@PathVariable("username") String username);
}
