# module-06-spring-aop · Spring AOP

> 学习面向切面编程：通知类型、切入点表达式、自定义注解 + 切面组合。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `aspect/LoggingAspect` | 四种通知：`@Before` / `@After` / `@AfterReturning` / `@AfterThrowing`、`@Pointcut` 复用 |
| `aspect/PerformanceAspect` | `@Around` 环绕通知、`@annotation()` 切入点、`ProceedingJoinPoint.proceed()` |
| `annotation/LogExecutionTime` | 自定义注解（`@Target`/`@Retention`） |
| `service/OrderService` | 被切面拦截的纯净业务代码（不含任何横切逻辑） |
| `controller/OrderController` | 手动触发切面的接口 |
| `AopTest` | 验证切面生效（观察测试日志） |

## 🚀 运行与测试

```bash
mvn spring-boot:run -pl module-06-spring-aop

# 手动触发切面
curl -X POST "http://localhost:8080/api/orders?product=手机&quantity=2"
curl http://localhost:8080/api/orders
curl http://localhost:8080/api/orders/999   # 触发异常通知

# 测试（观察控制台日志输出）
mvn test -pl module-06-spring-aop
```

## 🔍 核心概念讲解

### 1. 切入点表达式速查

| 表达式 | 含义 |
|--------|------|
| `execution(* com.study.aop.service.*.*(..))` | service 包下所有类的所有方法 |
| `execution(public * *(..))` | 所有 public 方法 |
| `execution(* *.create*(..))` | 所有以 create 开头的方法 |
| `@annotation(com.study.aop.annotation.LogExecutionTime)` | 标注了指定注解的方法 |
| `within(com.study.aop.service..*)` | service 包及子包中所有方法 |

### 2. 五种通知执行顺序
```
@Around 开始
  -> @Before
  -> 目标方法执行
  -> @AfterReturning（成功时）/ @AfterThrowing（异常时）
  -> @After（总是执行）
@Around 结束
```

### 3. 动态代理原理
- Spring AOP 基于**动态代理**（JDK 代理 / CGLIB）
- 只有通过 Spring 容器获取的 Bean 才有代理（`new` 出来的对象没有切面！）
- 同类内部调用 `this.method()` 不经过代理 → **切面不生效**（与 @Transactional 同款陷阱）

### 4. AOP vs AspectJ
- Spring AOP：基于代理，只支持方法级别，简单场景够用
- AspectJ：字节码织入，支持字段/构造器级别，更强大但侵入性高

## ✍️ 动手练习

1. 新增一个 `SecurityAspect`，用 `@Before` 模拟权限校验（无权限直接抛异常）。
2. 给 `OrderService.listOrders()` 也加上 `@LogExecutionTime`，观察计时输出。
3. 写一个缓存切面：`@Cacheable` 语义的自定义注解 + `@Around` 实现（先查缓存再执行方法）。
4. 用 `joinPoint.getSignature()` 打印方法的完整签名信息。
5. 在切面中获取方法参数并做参数校验（`@Before` + `joinPoint.getArgs()`）。
