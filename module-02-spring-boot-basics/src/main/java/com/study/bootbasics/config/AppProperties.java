package com.study.bootbasics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 类型安全的配置绑定：把 application.yml 中的配置映射为强类型对象
 *
 * 对应配置（application.yml）：
 *   app:
 *     name: java-spring-study
 *     version: 1.0.0
 *     author: zhangsan
 *     tags:
 *       - spring
 *       - java
 *     limits:
 *       max-users: 1000
 *       max-connections: 100
 *
 * 相比 @Value 逐个注入的优势：
 *   - 类型安全、分组清晰、可复用
 *   - 配合 spring-boot-configuration-processor 有 IDE 自动补全
 *   - 支持数据校验（配合 spring-boot-starter-validation 的 @Validated）
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String version,
        String author,
        List<String> tags,
        Map<String, Integer> limits
) {
}
