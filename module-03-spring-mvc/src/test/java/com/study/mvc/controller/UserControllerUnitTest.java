package com.study.mvc.controller;

import com.study.mvc.model.User;
import com.study.mvc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller 纯单元测试：不启动 Spring MVC，验证参数传递、状态码和返回结构。
 * HTTP 映射与参数校验仍由 UserControllerTest 的 MockMvc 测试覆盖。
 */
class UserControllerUnitTest {

    private UserService userService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        controller = new UserController(userService);
    }

    @Test
    void createShouldReturnCreatedResponse() {
        User request = new User(null, "张三", "zhangsan@example.com", 20, "13800138000");
        User saved = new User(1L, request.name(), request.email(), request.age(), request.phone());
        when(userService.create(request)).thenReturn(saved);

        var response = controller.create(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(saved);
        verify(userService).create(request);
    }

    @Test
    void listShouldAssemblePagingResponse() {
        List<User> users = List.of(new User(1L, "张三", "a@example.com", 20, null));
        when(userService.list(2, 10)).thenReturn(users);
        when(userService.count()).thenReturn(11L);

        Map<String, Object> result = controller.list(2, 10);

        assertThat(result).containsEntry("page", 2)
                .containsEntry("size", 10)
                .containsEntry("total", 11L)
                .containsEntry("items", users);
    }

    @Test
    void updateGetAndDeleteShouldDelegateToService() {
        User user = new User(1L, "张三", "a@example.com", 20, null);
        when(userService.getById(1L)).thenReturn(user);
        when(userService.update(1L, user)).thenReturn(user);

        assertThat(controller.getById(1L)).isEqualTo(user);
        assertThat(controller.update(1L, user)).isEqualTo(user);
        assertThat(controller.delete(1L).getStatusCode().value()).isEqualTo(204);

        verify(userService).getById(1L);
        verify(userService).update(1L, user);
        verify(userService).delete(1L);
    }
}
