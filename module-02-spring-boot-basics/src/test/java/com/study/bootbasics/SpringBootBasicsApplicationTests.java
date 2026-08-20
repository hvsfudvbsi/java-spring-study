package com.study.bootbasics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 最基础的 Spring Boot 测试：验证应用上下文能否正常加载
 *
 * @SpringBootTest 会启动完整的 Spring 容器（不启动 Web 服务器，默认 MOCK 模式）
 * 如果这个测试失败，说明配置或 Bean 定义有问题。
 */
@SpringBootTest
class SpringBootBasicsApplicationTests {

    @Test
    @DisplayName("应用上下文能正常加载：配置和 Bean 定义正确时容器启动成功")
    void contextLoads() {
        // 上下文能加载出来就说明配置正确
    }
}
