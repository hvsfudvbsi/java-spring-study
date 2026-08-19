package com.study.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 订单实体：多对一关系（多个订单属于一个用户）
 *
 * 关系映射三要素：
 *   1. @ManyToOne：关系类型（本表是多的一方）
 *   2. @JoinColumn(name = "user_id")：外键列
 *   3. fetch 策略：ManyToOne 默认 EAGER（急加载），因为多的一方通常都需要关联对象
 */
@Entity
@Table(name = "t_order")
public class Order {

    public enum OrderStatus {
        PENDING,   // 待支付
        PAID,      // 已支付
        SHIPPED,   // 已发货
        CANCELLED  // 已取消
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING) // 枚举以字符串形式存库（可读性好）
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /** 多对一：多个订单指向同一个用户（外键 user_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Order() {
    }

    public Order(String orderNo, BigDecimal amount) {
        this.orderNo = orderNo;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
