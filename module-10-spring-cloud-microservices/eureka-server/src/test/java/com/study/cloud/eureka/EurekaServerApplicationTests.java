package com.study.cloud.eureka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 注册中心上下文加载测试：
 * 验证 @EnableEurekaServer 和相关自动配置能正常装配（不启动真实端口）。
 *
 * 学习点：Eureka Server 是纯配置型组件，测试重点是"上下文能否组装成功"；
 * 真实的注册/发现行为需要启动全部服务后通过 8761 管理界面验证（见 README）。
 */
@SpringBootTest
class EurekaServerApplicationTests {

    @Test
    @DisplayName("Eureka Server 上下文能正常加载（@EnableEurekaServer 装配成功）")
    void contextLoads() {
        // 上下文能加载即说明注册中心配置正确
    }
}
