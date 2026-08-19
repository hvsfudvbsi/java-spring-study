# module-05-spring-security · Spring Security

> 学习认证与授权：BCrypt、SecurityFilterChain、方法级安全、JWT 无状态认证。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `config/SecurityConfig` | `SecurityFilterChain` 过滤器链、授权规则、BCrypt、内存用户、无状态 Session |
| `config/MethodSecurityConfig` | `@EnableMethodSecurity`、`@PreAuthorize` 方法级安全 |
| `security/JwtUtil` | JWT 结构（Header.Payload.Signature）、生成与校验 |
| `security/JwtAuthenticationFilter` | `OncePerRequestFilter`、解析 Bearer token、设置 SecurityContext |
| `controller/AuthController` | 登录认证流程、AuthenticationManager |
| `controller/DemoController` | 公开/私有/管理员三级接口 |

## 🚀 运行与测试

```bash
mvn spring-boot:run -pl module-05-spring-security

# 测试
mvn test -pl module-05-spring-security
```

### 演示用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ROLE_ADMIN, ROLE_USER |
| user | user123 | ROLE_USER |

### 接口测试（curl）

```bash
# 1. 登录，获取 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# => {"token":"eyJhbGciOiJIUzI1NiJ9...", "username":"admin", ...}

# 2. 公开接口（无需 token）
curl http://localhost:8080/api/public/hello

# 3. 私有接口（需要 token）
curl http://localhost:8080/api/private/hello \
  -H "Authorization: Bearer <token>"

# 4. 管理员接口（需要 ADMIN 角色，user 访问会 403）
curl http://localhost:8080/api/admin/hello \
  -H "Authorization: Bearer <admin的token>"

# 5. 查看当前用户
curl http://localhost:8080/api/me -H "Authorization: Bearer <token>"
```

## 🔍 核心概念讲解

### 1. 认证 vs 授权
- **认证（Authentication）**：验证"你是谁"——登录时校验用户名密码
- **授权（Authorization）**：验证"你能做什么"——检查角色/权限

### 2. JWT 无状态认证流程
```
客户端              服务端
  | -- POST /login --> 验证密码，签发 JWT
  | <-- {token} ------
  | -- GET /xxx + Bearer token --> JwtAuthenticationFilter 解析 token
  |                         --> 校验通过，放行
```

### 3. 密码安全
- **永远不要明文存密码**，用 BCrypt 加密（自带随机 salt，不可逆）
- 每次加密结果不同（salt 随机），但 `matches()` 能验证
- 生产环境密码算法可升级：`DelegatingPasswordEncoder`

### 4. 常见误区
- ❌ 把 JWT secret 写在代码里（应放环境变量）
- ❌ token 不设过期时间
- ❌ 用 Session 做无状态 API（应 STATELESS）

## ✍️ 动手练习

1. 新增一个只允许 `ROLE_USER` 访问的接口，验证 admin 反而不能访问。
2. 把 `JwtUtil` 的 token 中加入 `role` 声明，并在过滤器中使用。
3. 实现从数据库加载用户（替换 `InMemoryUserDetailsManager`，可参考 module-04 的 JPA）。
4. 为登录接口增加失败次数限制（防暴力破解）。
5. 用 `@PreAuthorize("hasRole('ADMIN') or hasRole('USER')")` 组合权限表达式。
