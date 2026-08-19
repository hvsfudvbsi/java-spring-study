package com.study.mvc.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc 集成测试：不启动真实服务器，模拟 HTTP 请求打到 Controller
 *
 * @SpringBootTest + @AutoConfigureMockMvc 组合。
 * 这是 Spring 后端最常用的测试方式。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createUser_shouldReturn201() throws Exception {
        String body = """
                {"name":"张三","email":"zhangsan@example.com","age":25,"phone":"13800138000"}
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("张三"));
    }

    @Test
    void createUser_withInvalidBody_shouldReturn400() throws Exception {
        // name 为空 + email 格式错误 + 手机号格式错误
        String body = """
                {"name":"","email":"not-an-email","age":25,"phone":"123"}
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void getUser_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/users/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void listUsers_shouldReturnPagedResult() throws Exception {
        // 先造 2 条数据
        for (int i = 0; i < 2; i++) {
            String body = """
                    {"name":"用户%d","email":"user%d@example.com","age":20,"phone":"13800138000"}
                    """.formatted(i, i);
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/users").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void deleteUser_shouldReturn204() throws Exception {
        // 先创建一个用户
        String body = """
                {"name":"待删除","email":"del@example.com","age":20,"phone":"13800138000"}
                """;
        String createResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult).get("id").asLong();

        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isNoContent());
    }
}
