package com.study.jpa.service;

import com.study.jpa.entity.Order;
import com.study.jpa.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** JPA 实体领域方法的纯单元测试，不需要数据库。 */
class UserDomainUnitTest {

    @Test
    @DisplayName("addOrder 维护双向关联：订单加入用户列表且订单指向同一用户")
    void addOrderShouldMaintainBothSidesOfAssociation() {
        User user = new User("张三", "zhangsan@example.com", 20);
        Order order = new Order("ORD-1", new BigDecimal("10.00"));

        user.addOrder(order);

        assertThat(user.getOrders()).containsExactly(order);
        assertThat(order.getUser()).isSameAs(user);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
    }

    @Test
    @DisplayName("订单状态默认 PENDING，可通过领域操作修改")
    void orderStatusShouldBeChangeableByDomainOperation() {
        Order order = new Order("ORD-2", new BigDecimal("20.00"));

        order.setStatus(Order.OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PAID);
    }

    @Test
    @DisplayName("多次 addOrder 按添加顺序维护多个订单且全部双向关联")
    void addMultipleOrdersShouldKeepOrderAndAssociation() {
        User user = new User("张三", "zhangsan@example.com", 20);
        Order first = new Order("ORD-1", new BigDecimal("10.00"));
        Order second = new Order("ORD-2", new BigDecimal("20.00"));

        user.addOrder(first);
        user.addOrder(second);

        assertThat(user.getOrders()).containsExactly(first, second);
        assertThat(first.getUser()).isSameAs(user);
        assertThat(second.getUser()).isSameAs(user);
    }
}
