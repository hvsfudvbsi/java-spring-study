# module-03-spring-mvc · Spring MVC / REST API

> 学习 Spring MVC 的核心能力：Controller、参数绑定、参数校验、全局异常处理、MockMvc 测试。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `model/User` | record + Bean Validation 校验注解（JSR-380） |
| `controller/UserController` | 完整 REST CRUD、`@PathVariable`/`@RequestParam`/`@RequestBody`、状态码语义化 |
| `controller/HelloController` | 参数绑定各种写法、默认值、数组参数、请求头读取 |
| `exception/GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常处理、统一错误响应 |
| `exception/UserNotFoundException` | 自定义业务异常 |
| `service/UserService` | 分层设计（Controller → Service → 数据源） |
| `UserControllerTest` | MockMvc 模拟 HTTP 请求测试 |

## 🚀 运行与测试

```bash
# 启动
mvn spring-boot:run -pl module-03-spring-mvc

# 测试
mvn test -pl module-03-spring-mvc
```

### 接口速查（启动后）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/users | 创建用户（body: JSON） |
| GET | /api/users/{id} | 查询单个用户 |
| GET | /api/users?page=1&size=10 | 分页查询 |
| PUT | /api/users/{id} | 更新用户 |
| DELETE | /api/users/{id} | 删除用户（返回 204） |
| GET | /api/demo/hello/张三?lang=cn | 参数绑定演示 |

### curl 示例

```bash
# 创建用户（校验通过）
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"张三","email":"zhangsan@example.com","age":25,"phone":"13800138000"}'

# 创建用户（校验失败 -> 400 + 统一错误格式）
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"","email":"bad","age":25,"phone":"123"}'

# 查询不存在 -> 404
curl http://localhost:8080/api/users/999
```

## 🔍 核心概念讲解

### 1. REST 设计规范
- **资源用名词复数**：`/api/users`，而不是 `/getUser`
- **HTTP 方法表达操作**：GET 查 / POST 增 / PUT 改 / DELETE 删
- **状态码语义化**：201 创建成功、204 删除成功、400 参数错误、404 不存在、500 服务器错误
- **无状态**：服务端不保存客户端状态（配合 JWT，见 module-05）

### 2. 参数校验流程
```
请求 JSON -> @Valid 触发校验 -> 不通过抛 MethodArgumentNotValidException
        -> GlobalExceptionHandler 捕获 -> 返回 400 + 统一错误格式
```

### 3. 全局异常处理的好处
- 业务代码只 `throw`，不关心响应格式
- 错误格式统一，前端解析简单
- 未处理异常统一记录日志

## ✍️ 动手练习

1. 给 `User` 增加字段（如 `birthday`），添加对应的校验注解。
2. 新增 `GET /api/users/search?keyword=` 模糊查询接口。
3. 为 `HelloController` 增加一个接收 `@RequestBody Map<String,Object>` 的接口。
4. 在 `GlobalExceptionHandler` 中增加对 `IllegalArgumentException` 的处理（返回 400）。
5. 为 `UserControllerTest` 补一个"更新用户"的测试用例。
