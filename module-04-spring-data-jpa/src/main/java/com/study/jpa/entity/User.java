package com.study.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 实体类：映射数据库表 t_user
 *
 * 映射规则（约定优于配置）：
 *   - 类名 User -> 表名 user（可通过 @Table(name="t_user") 指定）
 *   - 字段名 -> 列名（驼峰自动转下划线）
 *   - @Id 主键 + @GeneratedValue 自增策略
 *
 * 生命周期回调（面试）：
 *   @PrePersist / @PostPersist / @PreUpdate / @PostUpdate / @PreRemove / @PostRemove
 */
@Entity
@Table(name = "t_user")
@EntityListeners(AuditingEntityListener.class) // 启用审计字段自动填充
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    private Integer age;

    /** 审计字段：创建时间，插入时自动填充 */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** 审计字段：最后修改时间，更新时自动刷新 */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 一对多：一个用户有多个订单
     * mappedBy = "user"：由 Order 表的 user_id 外键维护关系（关系拥有方是 Order）
     * fetch = LAZY：懒加载，访问时才查数据库（避免无谓的联表查询）
     * cascade = ALL：级联操作，保存用户时级联保存订单
     */
    @OneToMany(mappedBy = "user")
    private List<Order> orders = new ArrayList<>();

    protected User() {
        // JPA 要求实体必须有无参构造器（protected 即可，防止外部直接 new）
    }

    public User(String name, String email, Integer age) {
        this.name = name;
        this.email = email;
        this.age = age;
    }

    /** 领域方法：添加订单（同时维护双向关联） */
    public void addOrder(Order order) {
        this.orders.add(order);
        order.setUser(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Order> getOrders() {
        return orders;
    }
}
