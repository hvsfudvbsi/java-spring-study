package com.study.bootbasics.component;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @SpringBootTest 注入真实 Bean 的集成测试
 * 对比：如果只测 GreetingService 的逻辑，可以用 @ExtendWith(MockitoExtension.class) + Mock 依赖
 */
@SpringBootTest
class GreetingServiceTest {

    @Autowired
    private GreetingService greetingService;

    @Test
    void greetContainsAppName() {
        String result = greetingService.greet("张三");
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("java-spring-study"));
    }

    @Test
    void showConfigReadsStructuredProperties() {
        String result = greetingService.showConfig();
        assertTrue(result.contains("[spring, java, boot]"), "应读取到 tags 列表，实际: " + result);
    }
}
