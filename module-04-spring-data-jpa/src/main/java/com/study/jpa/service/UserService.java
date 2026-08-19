package com.study.jpa.service;

import com.study.jpa.entity.Order;
import com.study.jpa.entity.User;
import com.study.jpa.repository.OrderRepository;
import com.study.jpa.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 业务层：演示事务管理（面试必问 @Transactional）
 *
 * 事务四大特性 ACID：原子性、一致性、隔离性、持久性
 *
 * @Transactional 关键点：
 *   1. 默认只对 RuntimeException 回滚，受检异常不回滚（可用 rollbackFor 指定）
 *   2. 默认传播行为 REQUIRED：有事务就加入，没有就新建
 *   3. 只对 public 方法生效，且必须通过代理调用（同类内部调用 this.method() 会失效！）
 *   4. 隔离级别：@Transactional(isolation = Isolation.REPEATABLE_READ)
 *   5. 只读优化：@Transactional(readOnly = true)（仅查询时用）
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public UserService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 演示事务回滚：创建用户 + 订单，中间抛异常则全部回滚
     */
    @Transactional
    public User createUserWithOrder(String name, String email, BigDecimal orderAmount) {
        User user = userRepository.save(new User(name, email, 20));

        Order order = new Order("ORD-" + System.currentTimeMillis(), orderAmount);
        user.addOrder(order);
        orderRepository.save(order);

        // 模拟业务校验失败 -> 整个事务回滚，用户和订单都不会入库
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("订单金额必须大于 0");
        }
        return user;
    }

    /** 只读事务：查询不需要加锁，性能更好 */
    @Transactional(readOnly = true)
    public List<User> search(String keyword) {
        return userRepository.findByNameContainingIgnoreCase(keyword);
    }

    /**
     * 演示传播行为：调用另一个事务方法
     * 默认 REQUIRED：外层有事务则加入外层事务
     */
    @Transactional
    public void updateUserEmail(Long id, String newEmail, String newName) {
        User user = userRepository.findById(id).orElseThrow();
        user.setEmail(newEmail);
        user.setName(newName);
        // 方法返回时事务提交，实体变更自动 flush 到数据库（脏检查机制）
    }

    /** 演示 REQUIRES_NEW：开启独立新事务（不参与外层事务） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void independentOperation() {
        // 即使外层事务回滚，这里已提交的更改也不会回滚
    }
}
