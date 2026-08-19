package com.study.jpa.repository;

import com.study.jpa.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单数据访问层
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 派生查询：查询某个用户的所有订单 */
    List<Order> findByUserId(Long userId);

    /** 派生查询：按状态查询 */
    List<Order> findByStatus(Order.OrderStatus status);

    /** 派生查询：金额大于某值 */
    List<Order> findByAmountGreaterThan(BigDecimal amount);
}
