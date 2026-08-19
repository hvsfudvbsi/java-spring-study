# module-04-spring-data-jpa · Spring Data JPA

> 学习 JPA 的核心能力：实体映射、Repository 派生查询、@Query、事务管理、审计（Auditing）。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `entity/User` | `@Entity` 映射、`@OneToMany` 一对多、审计字段 `@CreatedDate`/`@LastModifiedDate` |
| `entity/Order` | `@ManyToOne` 多对一、`@JoinColumn` 外键、`@Enumerated` 枚举映射 |
| `repository/UserRepository` | 派生查询、`@Query` 自定义 JPQL、分页 `Pageable`、`@Modifying` 修改 |
| `repository/OrderRepository` | 关联表查询（`findByUserId`） |
| `service/UserService` | `@Transactional` 事务、回滚、传播行为 `REQUIRES_NEW` |
| `UserRepositoryTest` | `@DataJpaTest` 切片测试（自动回滚，互不影响） |

## 🚀 运行与测试

```bash
# 启动（H2 内存库，无需安装数据库）
mvn spring-boot:run -pl module-04-spring-data-jpa

# H2 控制台（可查看表结构和数据）
# 浏览器打开 http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:studydb   用户名: sa   密码: (空)

# 测试
mvn test -pl module-04-spring-data-jpa
```

### 接口速查

```bash
# 创建用户+订单（演示事务）
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"张三","email":"zhangsan@example.com","amount":99.5}'

# 按邮箱查询
curl "http://localhost:8080/api/users/by-email?email=zhangsan@example.com"

# 名称模糊查询
curl "http://localhost:8080/api/users/search?keyword=张"

# 分页查询
curl "http://localhost:8080/api/users?page=0&size=5"
```

## 🔍 核心概念讲解

### 1. 派生查询命名规则（重点）
方法名 = 动词 + [条件] + 连接词 + [条件]...

| 方法名 | 生成的 SQL |
|--------|-----------|
| `findByEmail` | `WHERE email = ?` |
| `findByNameContaining` | `WHERE name LIKE '%' ? '%'` |
| `findByAgeBetween` | `WHERE age BETWEEN ? AND ?` |
| `countByAgeGreaterThan` | `SELECT COUNT(*) WHERE age > ?` |
| `findByUserId` | `WHERE user_id = ?`（关联表） |

### 2. 事务管理要点
- `@Transactional` 默认对 `RuntimeException` 回滚，受检异常需 `rollbackFor`
- 传播行为：`REQUIRED`（默认，加入外层事务）/ `REQUIRES_NEW`（开新事务）
- **陷阱**：同类内部调用 `this.method()` 不走代理，事务注解失效
- 只读查询加 `readOnly = true` 提升性能

### 3. 懒加载与 N+1 问题
- `@OneToMany` 默认 LAZY，访问集合时才查库
- 循环遍历 N 个父实体各查一次子表 = N+1 次查询（性能陷阱）
- 解决：`@EntityGraph` / `join fetch`（参见进阶学习）

## ✍️ 动手练习

1. 给 `User` 增加 `@OneToMany` 订单统计方法，在 Service 中计算用户订单总金额。
2. 新增 `findByAgeGreaterThanOrderByAgeDesc` 派生查询（注意排序写法）。
3. 写一个事务回滚验证：`createUserWithOrder` 传入负数金额，确认用户也没入库。
4. 在 `UserController` 中增加 `DELETE /api/users/{id}` 接口。
5. 试试把 `ddl-auto` 改为 `update`，观察表结构变化。
