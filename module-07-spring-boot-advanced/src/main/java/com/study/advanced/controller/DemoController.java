package com.study.advanced.controller;

import com.study.advanced.async.AsyncService;
import com.study.advanced.cache.CacheService;
import com.study.advanced.service.OrderService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示接口：缓存 / 异步 / 事件
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    private final CacheService cacheService;
    private final AsyncService asyncService;
    private final OrderService orderService;

    public DemoController(CacheService cacheService, AsyncService asyncService, OrderService orderService) {
        this.cacheService = cacheService;
        this.asyncService = asyncService;
        this.orderService = orderService;
    }

    // ---------- 缓存演示 ----------

    /** 连续访问两次，观察日志：第二次不再查询数据库 */
    @GetMapping("/user/{id}")
    public String getUser(@PathVariable Long id) {
        return cacheService.getUser(id);
    }

    @PutMapping("/user/{id}")
    public String updateUser(@PathVariable Long id, @RequestParam String name) {
        return cacheService.updateUser(id, name);
    }

    @DeleteMapping("/user/{id}")
    public Map<String, String> deleteUser(@PathVariable Long id) {
        cacheService.deleteUser(id);
        return Map.of("message", "已删除（缓存已清除）");
    }

    // ---------- 异步演示 ----------

    /** 接口立即返回，邮件在异步线程发送（看日志确认） */
    @PostMapping("/email")
    public Map<String, String> sendEmail(@RequestParam String to) {
        asyncService.sendEmail(to, "欢迎加入学习！");
        return Map.of("message", "邮件已提交，正在异步发送");
    }

    /** 等待异步任务结果（CompletableFuture.join） */
    @GetMapping("/report")
    public String report() {
        return asyncService.generateReport().join();
    }

    // ---------- 事件演示 ----------

    /** 创建订单，触发事件监听器（看日志确认同步+异步监听） */
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestParam String customer, @RequestParam double amount) {
        Long orderId = orderService.createOrder(customer, amount);
        return Map.of("orderId", orderId, "message", "订单创建成功");
    }
}
