package com.study.cloud.order.controller;

import com.study.cloud.order.client.UserClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单查询接口：返回订单 + 关联的用户信息（通过 Feign 调 user-service）
 *
 * 直接访问：http://localhost:8082/api/orders/1
 * 经网关访问：http://localhost:8080/api/order/orders/1
 *
 * 这演示了完整的服务链路：
 *   Client -> Gateway(8080) -> order-service(8082) -> Feign -> user-service(8081)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final UserClient userClient;

    public OrderController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) {
        // 服务间调用：获取下单用户的详细信息
        Map<String, Object> user = userClient.getUser(id);

        return Map.of(
                "orderId", id,
                "product", "商品-" + id,
                "amount", id * 100 + 0.5,
                "user", user,
                "from", "order-service"
        );
    }
}
