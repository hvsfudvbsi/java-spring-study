package com.study.cloud.order.controller;

import com.study.cloud.order.client.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 订单微服务 Controller 纯单元测试，不启动 Eureka 或真实 user-service。 */
class OrderControllerUnitTest {

    private UserClient userClient;
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        userClient = mock(UserClient.class);
        orderController = new OrderController(userClient);
    }

    @Test
    void getOrderShouldCombineOrderAndRemoteUser() {
        Map<String, Object> user = Map.of("id", 7L, "name", "用户7");
        when(userClient.getUser(7L)).thenReturn(user);

        Map<String, Object> result = orderController.getOrder(7L);

        assertThat(result)
                .containsEntry("orderId", 7L)
                .containsEntry("product", "商品-7")
                .containsEntry("amount", 700.5)
                .containsEntry("user", user)
                .containsEntry("from", "order-service");
        verify(userClient).getUser(7L);
    }
}
