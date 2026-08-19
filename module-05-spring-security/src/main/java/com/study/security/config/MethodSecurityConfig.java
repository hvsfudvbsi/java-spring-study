package com.study.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 开启方法级安全：在方法上用注解控制权限
 *
 * 常用注解：
 *   @PreAuthorize("hasRole('ADMIN')")            调用前检查
 *   @PreAuthorize("hasAuthority('user:read')")   权限（更细粒度）
 *   @PostAuthorize(...)                          调用后检查（可用于数据级权限）
 *   @Secured("ROLE_ADMIN")                       简单角色检查
 *
 * 示例：@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
 *   —— 支持 SpEL 表达式，可以做数据级权限控制
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
