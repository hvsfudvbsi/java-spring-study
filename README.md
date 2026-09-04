# java-spring-study 🍃

Java 21 + Spring Boot 3.5 多模块学习仓库：**从 Java 基础到 Spring Cloud 的一站式学习路径**。

> 外层仅是一个目录（父 POM），内层是 18 个相互独立的学习模块，每个模块聚焦一个主题，可单独编译、测试、运行。

## 📦 技术栈

| 技术 | 版本 |
|------|------|
| Java | 21 (LTS) |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 (Northfields) |
| 构建工具 | Maven（多模块） |
| 数据库 | H2（内存，零配置） |
| 测试 | JUnit 5 + Spring Boot Test |

## 🗂 模块总览（建议按编号顺序学习）

| 模块 | 主题 | 核心知识点 |
|------|------|-----------|
| [module-01-java-basics](module-01-java-basics) | Java 基础专题 | 集合、Stream、Optional、Lambda、泛型、record、并发（CompletableFuture） |
| [module-02-spring-boot-basics](module-02-spring-boot-basics) | Spring Boot 基础 | 自动配置、Starter、@ConfigurationProperties、Profile、Bean 生命周期 |
| [module-03-spring-mvc](module-03-spring-mvc) | Spring MVC / REST API | Controller、参数绑定、参数校验、@RestControllerAdvice 全局异常处理、MockMvc 测试 |
| [module-04-spring-data-jpa](module-04-spring-data-jpa) | Spring Data JPA | 实体映射、Repository 派生查询、@Query、事务、审计（Auditing） |
| [module-05-spring-security](module-05-spring-security) | Spring Security | 认证授权、BCrypt、SecurityFilterChain、方法级安全、JWT |
| [module-06-spring-aop](module-06-spring-aop) | Spring AOP | 切面编程、@Around/@Before 通知、自定义注解、切入点表达式 |
| [module-07-spring-boot-advanced](module-07-spring-boot-advanced) | Spring Boot 高级特性 | 缓存（Caffeine）、定时任务、@Async 异步、事件驱动、Actuator |
| [module-08-spring-cloud](module-08-spring-cloud) | Spring Cloud | OpenFeign 声明式调用、负载均衡、Resilience4j 熔断降级 |
| [module-09-message-queue](module-09-message-queue) | 消息队列 | Kafka（内嵌 broker 开箱即用）+ RabbitMQ（按需启用）、生产消费、交换机路由 |
| [module-10-spring-cloud-microservices](module-10-spring-cloud-microservices) | 微服务多服务演示 | Eureka 注册中心 + Gateway 网关 + 4 个独立服务、服务间 Feign 调用 |
| [module-11-netty](module-11-netty) | Netty 网络编程 | ByteBuf、Pipeline、Codec 粘包拆包、TCP Echo/Heartbeat/IM、HTTP、UDP、WebSocket、SSL/TLS、EmbeddedChannel 测试 |
| [module-12-multithreading](module-12-multithreading) | 多线程与并发 | Thread/线程池/锁/原子类/并发集合/CompletableFuture/虚拟线程 方法用例（常用+不常用）、生产者消费者、抢票、银行转账、高并发请求实操 |
| [module-13-design-patterns](module-13-design-patterns) | 设计模式 | GoF 23 种模式用例（常用+不常用写法）、在线商城/审批流/文档导出实操 |
| [module-14-spring-transaction](module-14-spring-transaction) | Spring 事务 | 声明式事务、提交/回滚、七种传播行为、隔离级别、只读事务、超时、编程式事务、多线程事务、代理调用陷阱 |
| [module-15-network](module-15-network) | 计算机网络 | 分层模型、TCP/UDP/IP/以太网报文首部逐位解析、TCP vs UDP 对比、JDK 原生 Socket 编程、粘包演示 |
| [module-16-spring-boot-netty](module-16-spring-boot-netty) | Spring Boot 集成 Netty | Tomcat 进程内嵌 Netty TCP 服务、共享 Spring Bean、独立进程 vs 同 JVM 嵌入选型 |
| [module-17-high-concurrency](module-17-high-concurrency) | 高并发实战 | 线程池调优（动态参数/监控）、TPS 提升（批量/零拷贝/连接池复用）、限流熔断（令牌桶/漏桶/滑动窗口/熔断器）、稳定性（优雅停机/资源隔离/超时/背压）、压测对比 |
| [module-19-classloader](module-19-classloader) | 类加载机制 | 加载生命周期、双亲委派、自定义/打破委派类加载器、SPI 与线程上下文类加载器、类冲突（两 jar 同名类）隔离方案、类加载/卸载与插件系统 |

## 📚 速查文档（docs/）

独立于模块的选型速查，从「要解决什么问题」出发快速定位方案：

| 文档 | 主题 | 来源模块 |
|------|------|---------|
| [docs/network-cheatsheet.md](docs/network-cheatsheet.md) | 网络协议选型：TCP vs UDP、分层模型、TCP 选项/状态机/拥塞控制、粘包解决、CIDR 边界、一分钟决策 | module-15-network |
| [docs/crypto-cheatsheet.md](docs/crypto-cheatsheet.md) | 密码学选型：HMAC vs CMAC、哈希/对称/非对称/签名/密钥协商、国密 SM2/SM3/SM4、一分钟决策 | module-18-bouncy-castle |

## 🚀 快速开始

### 环境要求
- JDK 21（[下载](https://adoptium.net/)）
- Maven 3.9+（或使用 IDEA 内置 Maven）

### 构建全部模块

```bash
mvn clean package
```

### 运行某个模块（以 Spring MVC 为例）

```bash
# 方式一：Maven 直接运行（Spring Boot 模块）
mvn spring-boot:run -pl module-03-spring-mvc

# 方式二：先打包成可执行 jar 再运行
mvn clean package -pl module-03-spring-mvc
java -jar module-03-spring-mvc/target/module-03-spring-mvc-1.0.0-SNAPSHOT.jar
```

> ⚠️ 提示：`module-01-java-basics` 是纯 Java 模块（无 Spring 依赖），运行方式见其 README。

### 运行测试

项目采用“单元测试 + 集成测试 + API 测试”分层，而不是只依赖接口调用：

- `*UnitTest`：纯 JUnit/Mockito 单元测试，不启动 Spring 容器、不访问数据库、消息中间件或外部服务，快速覆盖每个业务分支。
- `@DataJpaTest`、`@SpringBootTest`、`@EmbeddedKafka`：验证 JPA、事务、缓存、AOP、消息链路和 Bean 装配等框架集成行为。
- `MockMvc` 测试：验证 HTTP 路由、参数绑定、校验、认证授权和响应状态码。

```bash
# 全部测试（包含单元测试、切片测试、集成测试）
mvn test

# 单个模块全部测试
mvn test -pl module-04-spring-data-jpa

# 只运行某个模块的纯单元测试
mvn test -pl module-03-spring-mvc -Dtest='**/*UnitTest'

# 运行全项目纯单元测试；没有匹配测试的模块不失败
mvn test -Dtest='**/*UnitTest' -Dsurefire.failIfNoSpecifiedTests=false
```

新增业务功能时，建议先直接调用 Service/Domain/Worker 的具体方法编写 `*UnitTest`，用 Mock 隔离 Repository、Feign、消息客户端等依赖；Controller 不承担业务单元测试职责，HTTP 路由、参数校验和状态码统一由 MockMvc/API 集成测试验证。事务传播方法的业务分支用直接 `@Test` 调用验证，真实提交、回滚和保存点再由事务集成测试验证。

## 📚 学习建议

本仓库遵循[《学习内容规范》](学习规范.md)：每个知识点都应包含原理、执行流程、可运行示例、关键 API、常见陷阱和直接测试，目标是让你不依赖其他资料也能完成学习。

1. **按编号顺序学习**：先打牢 Java 基础（module-01），再学 Spring Boot 基础（module-02），之后按 MVC → JPA → Security → AOP → 高级特性 → Cloud 逐步深入。
2. **先读模块目录再读源码**：每个模块 README 必须列出完整知识点和文件映射；如果目录与源码不一致，应优先补齐文档，而不是默认跳过。
3. **善用测试**：优先阅读带 `@DisplayName` 的方法级测试，测试名称会说明输入、执行场景和预期结果；再阅读必要的框架集成测试。
4. **动手实验**：试着修改配置、增加接口、编写新的测试，实践是最好的学习方式。每个模块 README 的练习题都应能在当前项目中独立完成。

## 📄 License

MIT
