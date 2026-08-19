package com.study.bootbasics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot 应用入口
 *
 * @SpringBootApplication = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan
 *
 * 自动配置原理（面试必问）：
 *   1. @EnableAutoConfiguration 会加载 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *      中列出的所有自动配置类（如 WebMvcAutoConfiguration、DataSourceAutoConfiguration）
 *   2. 每个自动配置类都有 @ConditionalOnXxx 条件注解，满足条件才生效
 *      （例如没有配置数据源时，DataSourceAutoConfiguration 不生效）
 *   3. 这就是为什么"引入 Starter 依赖 + 少量配置"就能跑起来
 *
 * 条件注解（@ConditionalOnXxx）是理解自动配置的关键：
 *   @ConditionalOnClass / @ConditionalOnMissingBean / @ConditionalOnProperty / @ConditionalOnWebApplication
 */
@SpringBootApplication
@ConfigurationPropertiesScan // 扫描 @ConfigurationProperties 类并注册为 Bean
public class SpringBootBasicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootBasicsApplication.class, args);
    }
}
