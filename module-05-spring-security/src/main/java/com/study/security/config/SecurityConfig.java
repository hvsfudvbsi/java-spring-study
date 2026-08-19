package com.study.security.config;

import com.study.security.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：定义过滤器链、用户、密码加密器
 *
 * 过滤链执行流程（Spring Security 6）：
 *   请求 -> CsrfFilter -> 认证过滤器（JWT）-> AuthorizationFilter（授权检查）-> Controller
 *
 * 无状态 JWT 方案要点：
 *   - SessionCreationPolicy.STATELESS：不用 Session，每次请求带 token
 *   - csrf 禁用：无状态 API 没有 CSRF 攻击面（CSRF 针对 Cookie/Session）
 *   - 自定义 JwtAuthenticationFilter 在 UsernamePasswordAuthenticationFilter 之前执行
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 密码加密器：BCrypt（每次加密 salt 随机，不可逆） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 内存用户（学习用）。生产环境通常从数据库加载（UserDetailsService 实现见进阶）。
     * 密码是 BCrypt 加密后的密文。
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "USER")   // 角色：ROLE_ADMIN, ROLE_USER
                .build();

        UserDetails user = User.builder()
                .username("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    /**
     * 认证管理器：登录时校验用户名密码（AuthController 中注入使用）。
     * Spring Security 6 默认不会暴露 AuthenticationManager Bean，
     * 必须通过 AuthenticationConfiguration 显式获取（会基于上面的
     * UserDetailsService + PasswordEncoder 自动构建 DaoAuthenticationProvider）。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 核心：安全过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // 禁用 CSRF（无状态 JWT API）
                .csrf(csrf -> csrf.disable())
                // 无状态：不创建 Session
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 授权规则
                .authorizeHttpRequests(auth -> auth
                        // 登录接口放行
                        .requestMatchers("/api/auth/**").permitAll()
                        // 公开接口
                        .requestMatchers("/api/public/**").permitAll()
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                // 在用户名密码认证过滤器之前插入 JWT 过滤器
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // 使用无状态认证方式（不启用表单登录）
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
