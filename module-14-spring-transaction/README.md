# module-14-spring-transaction · Spring 事务

> 用 JdbcTemplate + H2 学习 Spring 声明式事务。代码刻意把外层事务和内层事务放在不同的 Bean 中，便于观察 Spring AOP 代理真正拦截到的事务边界。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `service/AccountService` | `@Transactional` 声明式事务、转账原子性、运行时异常回滚 |
| `service/TransactionPropagationService` | 外层事务与内层事务的 `REQUIRED`、`REQUIRES_NEW`、`NESTED` |
| `service/TransactionWorker` | 被代理的内层事务方法；演示独立提交和保存点回滚 |
| `repository/AccountRepository` | JdbcTemplate 更新账户余额 |
| `repository/TransactionLogRepository` | 写入、查询事务日志，直观看到最终提交结果 |
| `TransactionServiceTest` | 用集成测试验证提交、回滚和传播行为差异 |

## 🚀 运行与测试

```bash
# 启动（H2 内存库，无需安装数据库）
mvn spring-boot:run -pl module-14-spring-transaction

# 测试
mvn test -pl module-14-spring-transaction
```

启动后可以访问 H2 控制台：<http://localhost:8080/h2-console>

- JDBC URL：`jdbc:h2:mem:transactiondb`
- 用户名：`sa`
- 密码：空

### 接口速查

```bash
# 正常转账：两个账户余额在同一个事务中更新
curl -X POST "http://localhost:8080/api/transactions/transfer?fromId=1&toId=2&amount=10"

# 转账中途抛异常：扣款和入账一起回滚
curl -X POST "http://localhost:8080/api/transactions/transfer?fromId=1&toId=2&amount=1000"

# REQUIRED：内层加入外层，内层失败会导致整体回滚
curl -X POST "http://localhost:8080/api/transactions/propagation/required"

# REQUIRES_NEW：内层独立提交，即使外层随后回滚，内层日志仍存在
curl -X POST "http://localhost:8080/api/transactions/propagation/requires-new"

# NESTED：内层使用保存点回滚，外层捕获异常后仍可继续提交
curl -X POST "http://localhost:8080/api/transactions/propagation/nested"

# 查看最终提交的事务日志
curl http://localhost:8080/api/transactions/logs
```

## 🔍 核心概念

### 1. 事务四大特性（ACID）

- **原子性（Atomicity）**：转账的扣款和入账要么都成功，要么都失败。
- **一致性（Consistency）**：事务前后业务约束保持成立，例如账户余额不能为负。
- **隔离性（Isolation）**：并发事务互不干扰；隔离级别通过 `@Transactional(isolation = ...)` 指定。
- **持久性（Durability）**：事务提交后，数据不会因为后续业务异常而丢失。

### 2. 常用传播行为

传播行为描述：当前方法被另一个事务方法调用时，应该加入当前事务、挂起当前事务，还是创建新事务。

| 传播行为 | 当前有事务时 | 当前无事务时 | 典型用途 |
|----------|--------------|--------------|----------|
| `REQUIRED`（默认） | 加入当前事务 | 新建事务 | 普通业务方法 |
| `REQUIRES_NEW` | 挂起当前事务，创建新事务 | 新建事务 | 独立审计日志、通知记录 |
| `NESTED` | 创建保存点 | 新建事务 | 局部失败但允许外层继续 |
| `SUPPORTS` | 加入当前事务 | 非事务执行 | 可有可无的事务查询 |
| `NOT_SUPPORTED` | 挂起当前事务 | 非事务执行 | 明确不希望占用事务资源 |
| `MANDATORY` | 加入当前事务 | 抛出异常 | 强制调用方提供事务 |
| `NEVER` | 抛出异常 | 非事务执行 | 禁止在事务中调用 |

本模块的测试重点是前三种：

1. **REQUIRED**：外层和内层共用同一个物理事务。内层抛出运行时异常后，事务会被标记为 rollback-only；即使外层捕获异常，最终提交也可能抛出 `UnexpectedRollbackException`。
2. **REQUIRES_NEW**：外层事务先挂起，内层独立提交，然后恢复外层。外层回滚不会影响已提交的内层事务，但要注意连接池至少需要能同时提供外层和内层连接。
3. **NESTED**：在同一个物理事务中创建 JDBC 保存点。内层回滚只回到保存点，外层可以捕获异常并继续；它依赖事务管理器和底层数据库对保存点的支持。

### 3. `@Transactional` 常见属性

```java
@Transactional(
    propagation = Propagation.REQUIRED,
    isolation = Isolation.READ_COMMITTED,
    timeout = 5,
    readOnly = false,
    rollbackFor = IOException.class
)
```

- 默认只对 `RuntimeException` 和 `Error` 回滚，受检异常要用 `rollbackFor` 明确指定。
- `readOnly = true` 是对事务语义和数据库优化的提示，不是权限控制，也不等于绝对禁止写入。
- `timeout` 用于限制事务执行时间，具体效果取决于事务管理器和数据库驱动。

### 4. 代理调用陷阱

`@Transactional` 基于 Spring AOP 代理：

- 只有从 Spring 容器获取的 Bean，调用经过代理时注解才生效。
- 同一个类中的 `this.otherMethod()` 不会经过代理，`otherMethod()` 上的传播行为不会被重新解析。
- 因此本模块用 `TransactionPropagationService` 调用独立的 `TransactionWorker` Bean，而不是在同一个类里自调用。

## ✍️ 动手练习

1. 给转账方法增加 `rollbackFor = Exception.class`，再对比受检异常的默认行为。
2. 将 `REQUIRES_NEW` 的日志改为失败，观察外层是否会感知内层异常。
3. 把 H2 换成支持保存点的其他数据库，验证 `NESTED` 的数据库差异。
4. 为账户更新增加并发测试，比较 `READ_COMMITTED` 和 `SERIALIZABLE` 的效果。
5. 尝试把 `TransactionWorker` 的方法改成同类内部调用，验证代理失效问题。
