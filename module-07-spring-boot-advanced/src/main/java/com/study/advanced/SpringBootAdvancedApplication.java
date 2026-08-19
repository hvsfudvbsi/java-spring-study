package com.study.advanced;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 高级特性模块入口
 *
 * 本模块演示四个核心能力：
 *   1. 缓存（@EnableCaching + Caffeine）
 *   2. 异步（@EnableAsync + 自定义线程池）
 *   3. 定时任务（@EnableScheduling）
 *   4. 事件驱动（ApplicationEvent + @EventListener）
 */
@EnableCaching     // 开启注解缓存
@EnableAsync       // 开启 @Async 异步执行
@EnableScheduling  // 开启 @Scheduled 定时任务
@SpringBootApplication
public class SpringBootAdvancedApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootAdvancedApplication.class, args);
    }
}
