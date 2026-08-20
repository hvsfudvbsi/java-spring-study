package com.study.cloud.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 网关上下文加载测试：
 * 验证 Gateway 应用上下文和路由配置（application.yml 中的 lb:// 路由）能正常装配。
 *
 * 学习点：网关基于 WebFlux（响应式），@SpringBootTest 默认 MOCK 环境不启动真实端口，
 * 只验证 Bean 与路由定义装配成功；真实转发需要启动全部服务后通过 8080 验证（见 README）。
 */
@SpringBootTest
class GatewayApplicationTests {

    @Test
    @DisplayName("Gateway 上下文能正常加载（路由配置装配成功）")
    void contextLoads() {
        // 上下文能加载即说明网关路由配置正确
    }
}
