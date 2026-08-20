package com.study.cloud.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户服务 Web 层切片测试（@WebMvcTest，不启动 Eureka、不连数据库）：
 * 验证 /api/users/{id} 路由、参数绑定和响应 JSON 结构。
 *
 * 学习点：
 * - @WebMvcTest 只加载 Controller 层，比 @SpringBootTest 快且不依赖注册中心。
 * - jsonPath 断言响应 JSON 的每个字段，对应真实访问 http://localhost:8081/api/users/1 的结果。
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/users/1 返回用户 JSON（id/name/email/from=user-service）")
    void getUserShouldReturnUserJson() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("用户1"))
                .andExpect(jsonPath("$.email").value("user1@example.com"))
                .andExpect(jsonPath("$.from").value("user-service"));
    }

    @Test
    @DisplayName("GET /api/users/42 任意 id 都返回对应模拟用户（证明 @PathVariable 参数绑定）")
    void getUserShouldBindPathVariable() throws Exception {
        mockMvc.perform(get("/api/users/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("用户42"))
                .andExpect(jsonPath("$.email").value("user42@example.com"));
    }
}
