# module-08-spring-cloud · Spring Cloud

> 学习微服务三大基础能力：OpenFeign 声明式调用、负载均衡、Resilience4j 熔断降级。
> 本模块无需注册中心/网关，单应用即可运行学习。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `client/GithubClient` | `@FeignClient` 声明式 HTTP 调用（直连 GitHub 公开 API） |
| `client/RemoteServiceClient` | Feign 调用本地"模拟远程服务" |
| `controller/MockRemoteController` | 模拟远程服务：随机失败/随机变慢（制造熔断场景） |
| `service/ResilientService` | `@CircuitBreaker` 熔断 + `fallbackMethod` 降级 |
| `controller/DemoController` | 演示接口 |

## 🚀 运行与测试

```bash
mvn spring-boot:run -pl module-08-spring-cloud

# 1. 熔断降级演示（多调用几次，观察状态变化）
#    模拟远程服务 50% 概率失败/变慢，失败率超过阈值后熔断打开，
#    之后直接走降级方法（快速失败，不再调用远程）
for i in $(seq 1 20); do
  curl -s "http://localhost:8080/api/demo/call?name=张三$i" | head -c 120; echo
done

# 2. Feign 调用真实 GitHub API
curl "http://localhost:8080/api/demo/github/octocat"
```

## 🔍 核心概念讲解

### 1. 熔断器状态机（面试必问）
```
CLOSED（关闭）--失败率>50%--> OPEN（打开）
   ^                            |
   |                            | 10 秒后
   |                            v
   +--成功恢复<-- 试探成功 <--HALF_OPEN（半开）
```
- **CLOSED**：正常调用，统计窗口内失败率
- **OPEN**：快速失败，直接降级（保护下游）
- **HALF_OPEN**：放少量试探请求，成功则恢复，失败则回到 OPEN

### 2. 为什么需要熔断？
一个下游服务故障 → 调用方大量线程阻塞等待 → 线程耗尽 → 级联故障（雪崩）。
熔断 = 快速失败 + 降级返回，把故障隔离在局部。

### 3. Feign vs RestTemplate
| 对比 | RestTemplate | OpenFeign |
|------|-------------|-----------|
| 写法 | 手动拼 URL + 反序列化 | 接口 + 注解，声明式 |
| 可读性 | 差（URL 散落各处） | 好（接口即契约） |
| 负载均衡 | 需手动集成 | 内置（LoadBalancer） |

### 4. 微服务全家桶（进阶学习路径）
| 组件 | 作用 | 说明 |
|------|------|------|
| Eureka / Nacos | 服务注册与发现 | Feign 按服务名调用 |
| Spring Cloud Config / Nacos | 配置中心 | 配置动态刷新 |
| Spring Cloud Gateway | API 网关 | 路由、鉴权、限流统一入口 |
| Spring Cloud Sleuth / Micrometer | 链路追踪 | 排查跨服务调用问题 |
| Sentinel | 限流降级 | 阿里系，功能更全 |

## ✍️ 动手练习

1. 把熔断器的 `failureRateThreshold` 改成 80，观察熔断是否更难触发。
2. 新增一个 Feign 客户端调用 GitHub 的 `/repos/{owner}/{repo}` 接口。
3. 给 `GithubClient` 增加 `fallback` 降级实现（用 `@FeignClient(fallback = ...)`）。
4. 把 `MockRemoteController` 的失败概率调成 90%，再跑一次熔断演示。
5. 阅读 README 中"微服务全家桶"，用 Docker 启动一个 Nacos + 本模块对接注册中心。
