package com.study.advanced.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 自定义异步线程池
 *
 * 不配置的话，@Async 使用 SimpleAsyncTaskExecutor（每个任务新建线程，不推荐生产使用）。
 * 生产环境一定要自定义线程池，并配置拒绝策略。
 *
 * 线程池核心参数（面试必问）：
 *   corePoolSize      核心线程数（常驻）
 *   maxPoolSize       最大线程数（任务多时扩容到上限）
 *   queueCapacity     队列容量（核心线程满了先排队）
 *   keepAliveSeconds  非核心线程空闲存活时间
 *   拒绝策略：AbortPolicy（默认，抛异常）/ CallerRunsPolicy（调用线程执行）
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        // 队列满 + 线程达上限时：由调用方线程执行（保证任务不丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
