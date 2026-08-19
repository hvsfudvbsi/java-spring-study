# module-02-spring-boot-basics · Spring Boot 基础

> 学习 Spring Boot 的核心机制：自动配置、Starter、配置绑定、Profile、Bean 生命周期。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `SpringBootBasicsApplication` | `@SpringBootApplication` 三合一注解、自动配置原理、条件注解 |
| `config/AppConfig` | `@Configuration` + `@Bean` 手动声明 Bean、`@Primary`、Bean 生命周期 |
| `config/AppProperties` | `@ConfigurationProperties` 类型安全配置绑定（record 形式） |
| `component/GreetingService` | `@Service` 组件、构造器注入、`@Value` 注入单个配置项 |
| `component/ConditionalService` | `@ConditionalOnProperty` 条件化 Bean（自动配置核心机制） |
| `component/StartupRunner` | `CommandLineRunner` 启动后执行、`@Order` 排序 |
| `controller/HelloController` | 最简单的 REST 接口 |
| `application.yml` | 主配置；`application-dev.yml` Profile 覆盖机制 |

## 🚀 运行方式

```bash
# 启动应用（默认加载 application.yml）
mvn spring-boot:run -pl module-02-spring-boot-basics

# 指定 Profile 启动
mvn spring-boot:run -pl module-02-spring-boot-basics -Dspring-boot.run.arguments=--spring.profiles.active=dev

# 测试
mvn test -pl module-02-spring-boot-basics
```

启动后访问：
- http://localhost:8080/api/hello/张三 → 问候语
- http://localhost:8080/api/config → 读取的配置内容

## 🔍 核心概念讲解

### 1. 自动配置原理（面试必问）
1. `@SpringBootApplication` 中的 `@EnableAutoConfiguration` 通过
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   找到所有自动配置类。
2. 每个自动配置类都有 `@ConditionalOnXxx` 条件注解，**满足条件才生效**。
3. 例如：`DataSourceAutoConfiguration` 只有在类路径存在 `DataSource` 且未自定义数据源时才生效。
4. **结论**：Starter 依赖 + 少量配置 = 可运行的默认配置，这就是"约定优于配置"。

### 2. 依赖注入方式对比
| 方式 | 写法 | 适用场景 |
|------|------|---------|
| 构造器注入 | `public Xxx(Dep dep)` | ✅ 推荐（final 不可变、可测试） |
| Setter 注入 | `@Autowired setDep(...)` | 可选依赖 |
| 字段注入 | `@Autowired Dep dep` | ❌ 不推荐（隐藏依赖） |

### 3. Profile 机制
- `application-{profile}.yml` 存放环境差异配置（dev/test/prod）
- 激活方式：`--spring.profiles.active=dev` 或环境变量 `SPRING_PROFILES_ACTIVE`
- Profile 配置**覆盖**主配置中的同名项

## ✍️ 动手练习

1. 在 `application.yml` 中新增 `app.feature.demo-enabled: false`，观察 `ConditionalService` 是否还创建。
2. 新增一个 `@Bean`（如 `RestTemplate`），在 `GreetingService` 中注入并使用。
3. 添加 `application-prod.yml`，用 `--spring.profiles.active=prod` 启动，观察端口变化。
4. 在 `StartupRunner` 中打印 `@Value("${server.port}")`，理解启动时配置读取。
