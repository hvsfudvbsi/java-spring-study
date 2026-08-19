package com.study.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * 权限演示接口：展示不同级别的访问控制
 *
 * 三种接口：
 *   /api/public/**   公开（无需登录）
 *   /api/private/**  需要登录（任意角色）
 *   /api/admin/**    需要 ADMIN 角色（方法级安全 @PreAuthorize）
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    /** 公开接口：无需认证 */
    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "这是公开接口，无需登录");
    }

    /** 认证即可访问（配置中 anyRequest().authenticated() 保护） */
    @GetMapping("/private/hello")
    public Map<String, String> privateHello(Principal principal) {
        return Map.of(
                "message", "这是私有接口，需要登录",
                "currentUser", principal.getName()
        );
    }

    /** 方法级安全：只有 ADMIN 角色能访问 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/hello")
    public Map<String, String> adminHello() {
        return Map.of("message", "这是管理员接口，只有 ADMIN 能访问");
    }

    /** 查看当前登录用户信息 */
    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Map.of(
                "username", auth.getName(),
                "roles", auth.getAuthorities()
        );
    }
}
