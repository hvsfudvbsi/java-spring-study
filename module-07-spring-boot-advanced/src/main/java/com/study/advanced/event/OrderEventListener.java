package com.study.advanced.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 事件监听器：@EventListener 监听事件
 *
 * 特性：
 *   - 发布者（Publisher）不知道监听者是谁，监听者也不知道发布者是谁（完全解耦）
 *   - @Async：异步处理，不阻塞主流程（如发邮件通知）
 *   - @TransactionalEventListener：事务提交后再处理（避免读到未提交数据）
 *   - @Order：多个监听器的执行顺序
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    /** 同步监听：订单创建后记录日志 */
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("[事件-同步] 订单 {} 已创建，客户: {}，金额: {}", event.orderId(), event.customerName(), event.amount());
    }

    /** 异步监听：发短信通知（不阻塞主流程） */
    @Async("taskExecutor")
    @EventListener
    public void sendSms(OrderPlacedEvent event) {
        try {
            Thread.sleep(1000); // 模拟发短信耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[事件-异步] 发送短信给 {}: 您的订单 {} 已确认", event.customerName(), event.orderId());
    }
}
