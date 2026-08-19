package com.study.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全集成测试：验证认证和授权流程
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpringSecurityApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("公开接口无需登录")
    void publicEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/public/hello"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("私有接口未登录返回 401")
    void privateEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/private/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录成功返回 token")
    void loginSuccess() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", "user", "password", "user123"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("登录失败返回 401")
    void loginFailure() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", "user", "password", "wrong-password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("带 token 访问私有接口成功")
    void privateEndpointWithTokenWorks() throws Exception {
        // 1. 登录拿 token
        String loginBody = objectMapper.writeValueAsString(
                java.util.Map.of("username", "user", "password", "user123"));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();

        // 2. 带 token 访问私有接口
        mockMvc.perform(get("/api/private/hello")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUser").value("user"));
    }

    @Test
    @DisplayName("普通用户访问管理员接口被拒绝（403）")
    void userCannotAccessAdmin() throws Exception {
        // 1. user 登录
        String loginBody = objectMapper.writeValueAsString(
                java.util.Map.of("username", "user", "password", "user123"));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();

        // 2. 访问 admin 接口 -> 403 Forbidden
        mockMvc.perform(get("/api/admin/hello")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
