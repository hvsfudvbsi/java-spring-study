# module-07-spring-boot-advanced · Spring Boot 高级特性

> 学习生产必备的四大能力：缓存、异步、定时任务、事件驱动，以及 Actuator 监控。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `cache/CacheService` | `@Cacheable` / `@CachePut` / `@CacheEvict` 注解缓存、缓存 key 的 SpEL 规则 |
| `config/AsyncConfig` | 自定义线程池（核心/最大线程数、队列、拒绝策略） |
| `async/AsyncService` | `@Async` 异步方法、`CompletableFuture` 返回结果 |
| `schedule/ScheduledTask` | `@Scheduled` 定时任务、fixedRate / fixedDelay / cron 表达式 |
| `event/OrderPlacedEvent` | 事件驱动：`ApplicationEventPublisher` 发布 + `@EventListener` 监听（同步/异步） |
| `controller/DemoController` | 触发以上能力的演示接口 |
| Actuator | 健康检查、指标监控（`management.endpoints`） |

## 🚀 运行与测试

```bash
mvn spring-boot:run -pl module-07-spring-boot-advanced

# 缓存演示：访问两次，观察日志（第二次不再"查询数据库"）
curl http://localhost:8080/api/user/1
curl http://localhost:8080/api/user/1

# 异步演示：接口立即返回，邮件异步发送
curl -X POST "http://localhost:8080/api/email?to=test@example.com"

# 事件演示：看日志中同步 + 异步监听器的输出
curl -X POST "http://localhost:8080/api/orders?customer=张三&amount=99.5"

# Actuator 健康检查
curl http://localhost:8080/actuator/health

mvn test -pl module-07-spring-boot-advanced
```

## 🔍 核心概念讲解

### 1. 缓存三兄弟
| 注解 | 行为 | 场景 |
|------|------|------|
| `@Cacheable` | 先查缓存，未命中才执行方法 | 查询 |
| `@CachePut` | 总是执行方法，更新缓存 | 更新 |
| `@CacheEvict` | 清除缓存 | 删除 |

⚠️ 陷阱：同类内部调用 `this.getUser()` 缓存不生效（代理机制，与 @Transactional 相同）。

### 2. 缓存三大问题（面试必问）
- **穿透**：查不存在的数据 → 布隆过滤器 / 缓存空值
- **击穿**：热点 key 过期瞬间大量请求打到 DB → 互斥锁 / 逻辑过期
- **雪崩**：大量 key 同时过期 → 过期时间加随机值 / 集群

### 3. 异步 vs 定时 vs 事件
- **@Async**：调用方不等结果（发邮件、报表）
- **@Scheduled**：按时间触发（心跳、数据同步）
- **@EventListener**：按事件触发（下单后发短信）——解耦的利器

### 4. Actuator 常用端点
| 端点 | 用途 |
|------|------|
| /actuator/health | 健康检查（K8s 探针、负载均衡都用它） |
| /actuator/info | 应用信息 |
| /actuator/metrics | 指标（JVM 内存、线程、HTTP 请求数） |
| /actuator/caches | 缓存使用情况 |

## ✍️ 动手练习

1. 给 `CacheService` 增加一个 `getUser` 的缓存穿透处理（查不到时缓存空值）。
2. 写一个每天凌晨 3 点执行的 `@Scheduled` 任务（cron 表达式）。
3. 新增一个 `@TransactionalEventListener` 监听器，观察与普通 `@EventListener` 的区别。
4. 打开 `http://localhost:8080/actuator/metrics` 查看 JVM 内存指标。
5. 给异步任务加异常处理（`CompletableFuture.exceptionally` 或自定义 `AsyncUncaughtExceptionHandler`）。
