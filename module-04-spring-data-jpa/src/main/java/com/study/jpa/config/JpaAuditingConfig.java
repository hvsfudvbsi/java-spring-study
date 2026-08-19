package com.study.jpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 开启 JPA 审计（Auditing）：
 * 让 @CreatedDate / @LastModifiedDate / @CreatedBy 等注解自动填充，
 * 无需手动 set 创建时间。
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
