package com.study.cloud.order.controller;

import com.study.cloud.order.client.UserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单服务 Web 层切片测试（@WebMvcTest）：
 * 用 @MockitoBean 替换 Feign 客户端 UserClient，隔离 user-service，
 * 验证 OrderController 的路由、参数绑定和"订单 + 用户"响应组装。
 *
 * 学习点：
 * - Feign 接口是远程调用，单测中必须 Mock，否则会尝试真实连接 Eureka/user-service。
 * - 通过 verify 断言确实按 id 调用了 user-service（对应 Feign 的 /api/users/{id}）。
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

    @Test
    @DisplayName("GET /api/orders/1 组装订单 + Feign 获取的用户信息")
    void getOrderShouldAssembleUserAndOrder() throws Exception {
        Map<String, Object> user = Map.of(
                "id", 1L,
                "name", "用户1",
                "email", "user1@example.com",
                "from", "user-service");
        when(userClient.getUser(1L)).thenReturn(user);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.product").value("商品-1"))
                .andExpect(jsonPath("$.amount").value(100.5))
                .andExpect(jsonPath("$.from").value("order-service"))
                .andExpect(jsonPath("$.user.name").value("用户1"))
                .andExpect(jsonPath("$.user.from").value("user-service"));

        // 证明 Feign 客户端确实按订单 id 调用了 user-service
        verify(userClient).getUser(1L);
    }

    @Test
    @DisplayName("GET /api/orders/42 使用不同的 id 绑定并返回对应数据")
    void getOrderShouldBindPathVariableAndComputeAmount() throws Exception {
        when(userClient.getUser(42L)).thenReturn(Map.of("id", 42L, "name", "用户42"));

        mockMvc.perform(get("/api/orders/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.amount").value(4200.5));

        verify(userClient).getUser(42L);
    }
}
