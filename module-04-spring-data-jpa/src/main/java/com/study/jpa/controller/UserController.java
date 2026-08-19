package com.study.jpa.controller;

import com.study.jpa.entity.User;
import com.study.jpa.repository.UserRepository;
import com.study.jpa.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 简单的用户接口：展示 JPA 的查询能力
 * （详细的分层/校验/异常处理见 module-03）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /** 创建用户 + 订单（演示事务） */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String email = (String) body.get("email");
        BigDecimal amount = new BigDecimal(String.valueOf(body.getOrDefault("amount", "100")));
        return userService.createUserWithOrder(name, email, amount);
    }

    /** 按 id 查询 */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    /** 按邮箱查 */
    @GetMapping("/by-email")
    public User getByEmail(@RequestParam String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    /** 名称模糊查询 */
    @GetMapping("/search")
    public Iterable<User> search(@RequestParam String keyword) {
        return userService.search(keyword);
    }

    /** 分页查询：/api/users?page=0&size=5 */
    @GetMapping
    public Page<User> list(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return userRepository.findAll(PageRequest.of(page, size));
    }
}
