package com.study.aop.controller;

import com.study.aop.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 触发切面的接口：调用 OrderService 时观察控制台日志
 *
 * 访问示例：
 *   POST /api/orders?product=手机&quantity=2   -> 触发日志切面 + 性能切面
 *   GET  /api/orders                           -> 触发日志切面
 *   GET  /api/orders/999                       -> 触发异常切面
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Long create(@RequestParam String product, @RequestParam int quantity) {
        return orderService.createOrder(product, quantity);
    }

    @GetMapping
    public List<String> list() {
        return orderService.listOrders();
    }

    @GetMapping("/{id}")
    public String get(@PathVariable Long id) {
        return orderService.findOrder(id);
    }
}
