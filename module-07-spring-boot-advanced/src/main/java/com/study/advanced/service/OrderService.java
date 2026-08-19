package com.study.advanced.service;

import com.study.advanced.event.OrderPlacedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 事件发布方：通过 ApplicationEventPublisher 发布事件
 */
@Service
public class OrderService {

    private final ApplicationEventPublisher eventPublisher;

    public OrderService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public Long createOrder(String customerName, double amount) {
        Long orderId = System.currentTimeMillis();

        // 业务逻辑...
        // 发布事件：监听器会收到通知（同步 + 异步各一个）
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, customerName, amount));

        return orderId;
    }
}
