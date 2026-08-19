package com.study.cloud.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 模拟远程微服务：随机失败 / 随机变慢，用来演示熔断降级
 *
 * 真实微服务环境中，这就是另一个独立部署的服务
 * （比如订单服务调用用户服务）。这里放在同一个应用里方便学习。
 */
@RestController
@RequestMapping("/api/mock")
public class MockRemoteController {

    private static final Logger log = LoggerFactory.getLogger(MockRemoteController.class);

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam String name) throws InterruptedException {
        int rand = ThreadLocalRandom.current().nextInt(100);

        if (rand < 30) {
            // 30% 概率模拟变慢（超过熔断器的慢调用阈值）
            log.warn("[模拟远程服务] 响应变慢 2 秒...");
            TimeUnit.MILLISECONDS.sleep(2000);
            return Map.of("message", "hello " + name + "（慢响应）", "status", "SLOW");
        }

        if (rand < 50) {
            // 20% 概率模拟失败
            log.warn("[模拟远程服务] 内部错误!");
            throw new IllegalStateException("模拟远程服务故障");
        }

        return Map.of("message", "hello " + name, "status", "OK");
    }
}
