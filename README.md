# java-spring-study 🍃

Java 21 + Spring Boot 3.5 多模块学习仓库：**从 Java 基础到 Spring Cloud 的一站式学习路径**。

> 外层仅是一个目录（父 POM），内层是 8 个相互独立的学习模块，每个模块聚焦一个主题，可单独编译、测试、运行。

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

```bash
# 全部模块测试
mvn test

# 单个模块测试
mvn test -pl module-04-spring-data-jpa
```

## 📚 学习建议

1. **按编号顺序学习**：先打牢 Java 基础（module-01），再学 Spring Boot 基础（module-02），之后按 MVC → JPA → Security → AOP → 高级特性 → Cloud 逐步深入。
2. **每个模块都有 README**：包含知识点讲解、代码导读、动手练习，建议边读边改代码。
3. **善用测试**：每个模块都有测试用例，运行测试可以验证你的理解是否正确。
4. **动手实验**：试着修改配置、增加接口、编写新的测试，实践是最好的学习方式。

## 📄 License

MIT
