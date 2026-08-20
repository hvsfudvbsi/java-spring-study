package com.study.bootbasics.component;

import com.study.bootbasics.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GreetingService 的纯单元测试：不启动 Spring 容器，直接构造被测对象。
 */
class GreetingServiceUnitTest {

    private GreetingService greetingService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "java-spring-study",
                "1.0.0",
                "张三",
                List.of("spring", "java"),
                Map.of("max-users", 1000));
        greetingService = new GreetingService(properties);
        // appName 来自 @Value，纯单元测试中直接注入测试值。
        ReflectionTestUtils.setField(greetingService, "appName", "java-spring-study");
    }

    @Test
    void greetShouldCombineNameApplicationAndAuthor() {
        assertThat(greetingService.greet("李四"))
                .isEqualTo("Hello, 李四! 欢迎学习 java-spring-study (作者: 张三)");
    }

    @Test
    void showConfigShouldRenderStructuredProperties() {
        assertThat(greetingService.showConfig())
                .contains("[spring, java]")
                .contains("max-users=1000");
    }
}
