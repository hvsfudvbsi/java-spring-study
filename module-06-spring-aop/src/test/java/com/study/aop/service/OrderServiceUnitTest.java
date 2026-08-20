package com.study.aop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderService 纯单元测试；AOP 通知链由 AopTest 集成测试单独验证。
 */
class OrderServiceUnitTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    @Test
    void createOrderShouldStoreFormattedOrder() {
        Long id = orderService.createOrder("手机", 2);

        assertThat(id).isPositive();
        assertThat(orderService.listOrders()).contains("手机 x2");
    }

    @Test
    void findOrderShouldReturnExistingOrder() {
        Long id = orderService.createOrder("电脑", 1);

        assertThat(orderService.findOrder(id)).isEqualTo("电脑 x1");
    }

    @Test
    void findMissingOrderShouldThrow() {
        assertThatThrownBy(() -> orderService.findOrder(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("订单不存在: 999");
    }
}
