package com.study.cloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载测试：验证 Feign 客户端、熔断器配置、Bean 装配正常
 */
@SpringBootTest
class SpringCloudApplicationTests {

    @Test
    @DisplayName("应用上下文能正常加载：Feign 代理与熔断器配置装配成功")
    void contextLoads() {
        // Feign 代理 + Resilience4j 配置加载成功即通过
    }
}
