package com.study.advanced.service;

import com.study.advanced.event.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** OrderService 纯单元测试：验证事件内容，不启动 Spring 事件容器。 */
class OrderServiceUnitTest {

    @Test
    void createOrderShouldPublishOrderPlacedEvent() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        OrderService orderService = new OrderService(publisher);

        Long orderId = orderService.createOrder("张三", 199.90);

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        OrderPlacedEvent event = captor.getValue();
        assertThat(orderId).isEqualTo(event.orderId());
        assertThat(event.customerName()).isEqualTo("张三");
        assertThat(event.amount()).isEqualTo(199.90);
    }
}
