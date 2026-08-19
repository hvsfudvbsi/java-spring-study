package com.study.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Security 学习模块入口
 *
 * 核心概念（面试必问）：
 *   认证（Authentication）：你是谁？—— 登录，验证用户名密码
 *   授权（Authorization）：你能做什么？—— 检查角色/权限
 *
 * 核心组件：
 *   SecurityFilterChain  安全过滤器链（每个请求都经过）
 *   UserDetailsService   用户信息加载
 *   PasswordEncoder      密码加密（BCrypt）
 *   SecurityContext      当前登录用户上下文（ThreadLocal）
 *   AuthenticationManager 认证管理器
 */
@SpringBootApplication
public class SpringSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSecurityApplication.class, args);
    }
}
