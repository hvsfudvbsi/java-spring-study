package com.study.mvc.service;

import com.study.mvc.exception.UserNotFoundException;
import com.study.mvc.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserService 纯单元测试：不启动 Web 层，直接覆盖业务方法和异常分支。
 */
class UserServiceUnitTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService();
    }

    @Test
    void createGetAndCountShouldWork() {
        User saved = userService.create(user("张三", "zhangsan@example.com"));

        assertThat(saved.id()).isEqualTo(1L);
        assertThat(userService.getById(1L)).isEqualTo(saved);
        assertThat(userService.count()).isEqualTo(1L);
    }

    @Test
    void listShouldReturnRequestedPage() {
        userService.create(user("张三", "zhangsan@example.com"));
        userService.create(user("李四", "lisi@example.com"));
        userService.create(user("王五", "wangwu@example.com"));

        List<User> page = userService.list(2, 2);

        assertThat(page).extracting(User::name).containsExactly("王五");
    }

    @Test
    void updateShouldReplaceDataAndKeepId() {
        userService.create(user("张三", "old@example.com"));

        User updated = userService.update(1L, user("李四", "new@example.com"));

        assertThat(updated).isEqualTo(new User(1L, "李四", "new@example.com", 20, "13800138000"));
        assertThat(userService.getById(1L)).isEqualTo(updated);
    }

    @Test
    void deleteShouldRemoveExistingUser() {
        userService.create(user("张三", "zhangsan@example.com"));

        userService.delete(1L);

        assertThat(userService.count()).isZero();
        assertThatThrownBy(() -> userService.getById(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void missingUserOperationsShouldThrowDomainException() {
        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> userService.update(99L, user("张三", "a@example.com")))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    private User user(String name, String email) {
        return new User(null, name, email, 20, "13800138000");
    }
}
