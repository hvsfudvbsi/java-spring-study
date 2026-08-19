package com.study.cloud.service;

import com.study.cloud.client.RemoteServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 熔断降级服务：Resilience4j @CircuitBreaker
 *
 * 熔断器三种状态（面试必问）：
 *   CLOSED（关闭）  正常调用，统计失败率
 *   OPEN（打开）    失败率超阈值，直接拒绝请求（快速失败，不再调用远程）
 *   HALF_OPEN（半开） 过一段时间放少量请求试探，成功则恢复 CLOSED
 *
 * 配置见 application.yml 的 resilience4j 部分：
 *   failureRateThreshold 失败率阈值（50%）
 *   slidingWindowSize    统计窗口（最近 10 次）
 *   waitDurationInOpenState 打开后等待 10 秒再试探
 *
 * fallbackMethod：熔断/异常时执行的降级方法
 *   签名要求：参数 + (Throwable) 或与业务方法完全一致
 */
@Service
public class ResilientService {

    private static final Logger log = LoggerFactory.getLogger(ResilientService.class);

    private final RemoteServiceClient remoteServiceClient;

    public ResilientService(RemoteServiceClient remoteServiceClient) {
        this.remoteServiceClient = remoteServiceClient;
    }

    /** 业务方法：被 @CircuitBreaker 保护，name 对应 application.yml 中的实例名 */
    @CircuitBreaker(name = "remoteService", fallbackMethod = "fallback")
    public Map<String, Object> callRemote(String name) {
        log.info(">>> 发起远程调用: {}", name);
        return remoteServiceClient.callHello(name);
    }

    /** 降级方法：远程失败/熔断打开时执行 */
    public Map<String, Object> fallback(String name, Throwable t) {
        log.warn(">>> 触发降级: {}，原因: {}", name, t.getMessage());
        return Map.of(
                "message", "服务暂时不可用，已降级返回（缓存/默认值）",
                "status", "FALLBACK",
                "requestedName", name
        );
    }
}
