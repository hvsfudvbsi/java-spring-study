package com.study.jpa.service;

import com.study.jpa.entity.Order;
import com.study.jpa.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** JPA 实体领域方法的纯单元测试，不需要数据库。 */
class UserDomainUnitTest {

    @Test
    void addOrderShouldMaintainBothSidesOfAssociation() {
        User user = new User("张三", "zhangsan@example.com", 20);
        Order order = new Order("ORD-1", new BigDecimal("10.00"));

        user.addOrder(order);

        assertThat(user.getOrders()).containsExactly(order);
        assertThat(order.getUser()).isSameAs(user);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
    }

    @Test
    void orderStatusShouldBeChangeableByDomainOperation() {
        Order order = new Order("ORD-2", new BigDecimal("20.00"));

        order.setStatus(Order.OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PAID);
    }
}
