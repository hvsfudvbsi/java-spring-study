package com.study.aop;

import com.study.aop.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AOP 生效验证测试：
 * 观察测试日志，可以看到 LoggingAspect 的 4 种通知和 PerformanceAspect 的耗时输出
 */
@SpringBootTest
class AopTest {

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("集成测试：createOrder 被日志/性能切面拦截（观察控制台 4 种通知日志）")
    void createOrder_shouldBeInterceptedByAspects() {
        // 运行后看控制台日志：
        // [日志-前] ... createOrder()
        // [性能监控] ... createOrder() 耗时 xx ms
        // [日志-返回] ... 返回值
        // [日志-结束] ... 执行完毕
        Long id = orderService.createOrder("手机", 2);
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("集成测试：findOrder 抛异常触发 @AfterThrowing 通知（观察异常日志）")
    void exception_shouldTriggerAfterThrowing() {
        // 运行后看控制台日志：[日志-异常] ... findOrder() 抛出异常
        assertThrows(IllegalArgumentException.class, () -> orderService.findOrder(999L));
    }

    @Test
    @DisplayName("集成测试：listOrders 在共享单例下仍包含刚创建的订单")
    void listOrders_shouldWork() {
        orderService.createOrder("电脑", 1);
        // 注意：@SpringBootTest 共享同一个 OrderService 单例，
        // 其他测试可能已创建订单，所以断言要自包含（不依赖全局数量）
        boolean containsNewOrder = orderService.listOrders().stream()
                .anyMatch(o -> o.contains("电脑"));
        assertTrue(containsNewOrder, "列表中应包含刚创建的订单");
    }
}
