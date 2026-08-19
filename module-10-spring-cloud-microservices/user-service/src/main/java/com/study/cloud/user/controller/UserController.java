package com.study.cloud.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户查询接口（模拟数据，不连数据库）
 *
 * 直接访问：http://localhost:8081/api/users/1
 * 经网关访问：http://localhost:8080/api/user/users/1
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Map<String, Object> getUser(@PathVariable Long id) {
        return Map.of(
                "id", id,
                "name", "用户" + id,
                "email", "user" + id + "@example.com",
                "from", "user-service"
        );
    }
}
