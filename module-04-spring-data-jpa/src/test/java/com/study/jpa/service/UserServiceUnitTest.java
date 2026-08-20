package com.study.jpa.service;

import com.study.jpa.entity.Order;
import com.study.jpa.entity.User;
import com.study.jpa.repository.OrderRepository;
import com.study.jpa.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 纯单元测试：Repository 使用 Mock，事务提交/回滚由 JPA 集成测试负责。
 */
class UserServiceUnitTest {

    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        orderRepository = mock(OrderRepository.class);
        userService = new UserService(userRepository, orderRepository);
    }

    @Test
    @DisplayName("创建用户+订单：两个实体都被保存且订单关联用户")
    void createUserWithOrderShouldBuildBothEntities() {
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUserWithOrder(
                "张三", "zhangsan@example.com", new BigDecimal("99.90"));

        assertThat(user.getName()).isEqualTo("张三");
        assertThat(user.getOrders()).singleElement()
                .satisfies(order -> {
                    assertThat(order.getAmount()).isEqualByComparingTo("99.90");
                    assertThat(order.getUser()).isSameAs(user);
                });
        verify(userRepository).save(any(User.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("订单金额为零时抛出 IllegalArgumentException 拒绝创建")
    void nonPositiveOrderAmountShouldRejectRequest() {
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> userService.createUserWithOrder(
                "张三", "zhangsan@example.com", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("订单金额必须大于 0");
    }

    @Test
    @DisplayName("搜索关键字直接委托给 Repository 的忽略大小写模糊查询")
    void searchShouldDelegateToRepository() {
        List<User> users = List.of(new User("张三", "zhangsan@example.com", 20));
        when(userRepository.findByNameContainingIgnoreCase("张")).thenReturn(users);

        assertThat(userService.search("张")).containsExactlyElementsOf(users);
        verify(userRepository).findByNameContainingIgnoreCase("张");
    }

    @Test
    @DisplayName("更新已存在用户：修改托管实体的邮箱和姓名")
    void updateUserEmailShouldChangeManagedEntity() {
        User user = new User("旧名字", "old@example.com", 20);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUserEmail(1L, "new@example.com", "新名字");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getName()).isEqualTo("新名字");
    }

    @Test
    @DisplayName("更新不存在的用户抛出 NoSuchElementException")
    void updateMissingUserShouldThrow() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserEmail(1L, "new@example.com", "新名字"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
