package com.study.aop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("创建订单返回正数 id 且列表包含格式化后的订单")
    void createOrderShouldStoreFormattedOrder() {
        Long id = orderService.createOrder("手机", 2);

        assertThat(id).isPositive();
        assertThat(orderService.listOrders()).contains("手机 x2");
    }

    @Test
    @DisplayName("按 id 查询已存在订单返回格式化内容")
    void findOrderShouldReturnExistingOrder() {
        Long id = orderService.createOrder("电脑", 1);

        assertThat(orderService.findOrder(id)).isEqualTo("电脑 x1");
    }

    @Test
    @DisplayName("查询不存在的订单抛出 IllegalArgumentException 并带订单号")
    void findMissingOrderShouldThrow() {
        assertThatThrownBy(() -> orderService.findOrder(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("订单不存在: 999");
    }
}
