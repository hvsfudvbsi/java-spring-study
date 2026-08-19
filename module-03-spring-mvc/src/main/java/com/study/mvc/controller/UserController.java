package com.study.mvc.controller;

import com.study.mvc.model.User;
import com.study.mvc.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST 用户接口：完整 CRUD + 分页
 *
 * REST 设计要点：
 *   - 资源用名词复数：/api/users
 *   - 用 HTTP 方法表达操作：GET 查询 / POST 新增 / PUT 更新 / DELETE 删除
 *   - 状态码语义化：201 创建成功 / 204 删除成功 / 400 参数错误 / 404 资源不存在
 *
 * 常用注解：
 *   @PathVariable  路径参数  /api/users/{id}
 *   @RequestParam  查询参数  /api/users?page=1&size=10
 *   @RequestBody   请求体（JSON -> 对象）
 *   @Valid         触发参数校验
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** POST /api/users 创建用户 */
    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody User user) {
        User saved = userService.create(user);
        // 201 Created + 返回创建的资源
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** GET /api/users/{id} 查询单个 */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    /** GET /api/users?page=1&size=10 分页查询 */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        List<User> users = userService.list(page, size);
        return Map.of(
                "page", page,
                "size", size,
                "total", userService.count(),
                "items", users
        );
    }

    /** PUT /api/users/{id} 更新（全量更新） */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @Valid @RequestBody User user) {
        return userService.update(id, user);
    }

    /** DELETE /api/users/{id} 删除 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
