package com.study.aop.service;

import com.study.aop.annotation.LogExecutionTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务服务：本身不包含任何日志/监控代码
 * —— AOP 的价值就在这里：横切逻辑集中到切面，业务代码保持纯净
 *
 * 会被两个切面拦截：
 *   - LoggingAspect（execution 表达式匹配 service 包所有方法）
 *   - PerformanceAspect（@LogExecutionTime 标注的方法）
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Map<Long, String> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    /** 会被日志切面拦截 + 性能切面计时 */
    @LogExecutionTime
    public Long createOrder(String product, int quantity) {
        // 模拟耗时业务
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Long id = idGen.getAndIncrement();
        orders.put(id, "%s x%d".formatted(product, quantity));
        return id;
    }

    /** 会被日志切面拦截 */
    public List<String> listOrders() {
        return List.copyOf(orders.values());
    }

    /** 演示 @AfterThrowing：抛异常时被切面记录 */
    public String findOrder(Long id) {
        String order = orders.get(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + id);
        }
        return order;
    }
}
