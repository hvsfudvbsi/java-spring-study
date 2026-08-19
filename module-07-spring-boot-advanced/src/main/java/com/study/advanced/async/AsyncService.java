package com.study.advanced.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 异步服务：@Async 让方法在独立线程执行，不阻塞调用方
 *
 * 典型场景：
 *   - 发送邮件 / 短信（响应不等待）
 *   - 日志上报、消息通知
 *   - 耗时的报表生成
 *
 * 使用注意：
 *   - 必须在 @EnableAsync 下才生效
 *   - 同类内部调用 this.method() 不生效
 *   - 异步方法返回 void 或 CompletableFuture（可拿结果/异常）
 *   - 异常在异步线程中抛出，调用方感知不到（用 CompletableFuture 或自定义 handler 处理）
 */
@Service
public class AsyncService {

    private static final Logger log = LoggerFactory.getLogger(AsyncService.class);

    /** 模拟发送邮件：调用方立即返回，实际在异步线程池中执行 */
    @Async("taskExecutor")
    public void sendEmail(String to, String content) {
        try {
            log.info(">>> [异步线程:{}] 开始发送邮件给 {}", Thread.currentThread().getName(), to);
            TimeUnit.MILLISECONDS.sleep(2000); // 模拟 2 秒耗时
            log.info(">>> [异步线程:{}] 邮件发送完成: {}", Thread.currentThread().getName(), content);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 返回 CompletableFuture：调用方可以等待结果 */
    @Async("taskExecutor")
    public java.util.concurrent.CompletableFuture<String> generateReport() {
        try {
            TimeUnit.MILLISECONDS.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return java.util.concurrent.CompletableFuture.completedFuture("报表数据生成完毕");
    }
}
