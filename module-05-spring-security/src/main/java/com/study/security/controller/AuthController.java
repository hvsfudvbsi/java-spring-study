package com.study.security.controller;

import com.study.security.model.LoginRequest;
import com.study.security.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：POST /api/auth/login
 *
 * 认证流程：
 *   1. AuthenticationManager 校验用户名密码
 *   2. 校验通过 -> 生成 JWT
 *   3. 客户端保存 token，后续请求带 Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        // 1. 认证：用户名密码错误会抛 BadCredentialsException
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.username(),
                        loginRequest.password()));

        // 2. 认证成功，把认证信息放入上下文（当前线程）
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. 生成 token 返回给客户端
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails.getUsername());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", userDetails.getUsername(),
                "roles", userDetails.getAuthorities()
        ));
    }
}
