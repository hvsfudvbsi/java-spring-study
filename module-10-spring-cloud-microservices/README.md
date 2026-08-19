# module-10-spring-cloud-microservices · 微服务多服务演示

> 完整的微服务调用链路：**Eureka 注册中心 + Spring Cloud Gateway 网关 + 服务间 Feign 调用**。
> 4 个独立可运行的 Spring Boot 服务，全部基于 Spring Cloud 2025.0.3。

## 🏗 架构图

```
                        ┌─────────────────────────────┐
                        │   Eureka Server (8761)      │  注册中心
                        │   服务注册 / 服务发现        │
                        └──────────▲──────────▲───────┘
                                   │注册       │注册
        ┌───────────┐  路由   ┌────┴────┐  ┌──┴─────────┐
Client──▶│ api-gateway│───────▶│order-svc │─▶│ user-svc   │
        │  (8080)   │ lb://   │ (8082)  │  │  (8081)    │
        └───────────┘         └─────────┘  └────────────┘
        统一入口/路由          订单服务 Feign调用 用户服务
```

**调用链路**：`Client → Gateway(8080) → order-service(8082) → Feign → user-service(8081)`

## 📦 服务清单

| 服务 | 端口 | 职责 | 关键依赖 |
|------|------|------|---------|
| `eureka-server` | 8761 | 服务注册中心 | `spring-cloud-starter-netflix-eureka-server` |
| `user-service` | 8081 | 用户服务（微服务 A） | `eureka-client` |
| `order-service` | 8082 | 订单服务（微服务 B） | `eureka-client` + `openfeign` + `loadbalancer` |
| `api-gateway` | 8080 | API 网关（统一入口） | `gateway`（WebFlux）+ `eureka-client` + `loadbalancer` |

## 🚀 启动步骤（按顺序）

需要 4 个终端，分别启动 4 个服务：

```bash
# 1. 注册中心
mvn spring-boot:run -pl module-10-spring-cloud-microservices/eureka-server

# 2. 用户服务
mvn spring-boot:run -pl module-10-spring-cloud-microservices/user-service

# 3. 订单服务
mvn spring-boot:run -pl module-10-spring-cloud-microservices/order-service

# 4. 网关
mvn spring-boot:run -pl module-10-spring-cloud-microservices/api-gateway
```

> 也可以先整体编译：`mvn clean package -pl module-10-spring-cloud-microservices -am`
> 然后用 `java -jar` 分别启动各服务的可执行 jar。

## ✅ 验证

```bash
# 1. 注册中心管理界面：应看到 user-service / order-service / api-gateway 三个实例
open http://localhost:8761

# 2. 直接访问用户服务
curl http://localhost:8081/api/users/1

# 3. 直接访问订单服务（内部 Feign 调用户服务）
curl http://localhost:8082/api/orders/1

# 4. 经网关访问（统一入口 + 路由转发）
curl http://localhost:8080/api/user/users/1
curl http://localhost:8080/api/order/orders/1
# => 返回订单 + 关联用户信息，证明整条链路打通
```

## 🔍 核心概念讲解

### 1. 服务注册与发现（Eureka）
- 服务启动 → 向 Eureka 注册自己的 IP:端口
- 服务下线 → 自动注销（心跳机制，默认 30 秒）
- 消费者通过服务名 `user-service` 查询实例列表，而不是写死 IP
- **好处**：实例增减、扩缩容对调用方透明

### 2. 网关路由（Spring Cloud Gateway）
- 客户端只认识网关，不直接访问微服务（安全边界）
- `uri: lb://user-service`：`lb://` 前缀 = 经 LoadBalancer 从注册中心解析地址
- `StripPrefix=2`：去掉路径前两段再转发
- 网关可统一做：鉴权、限流、日志、跨域

### 3. 服务间调用（OpenFeign）
- `@FeignClient(name = "user-service")`：按服务名调用，不写死地址
- 配合 Eureka 服务发现 + LoadBalancer 负载均衡
- 对比 module-08 的 `url=` 直连写法：那只是学习用，微服务正确方式是服务名

### 4. 为什么网关是独立应用？
Spring Cloud Gateway 基于 **WebFlux（响应式）**，不能和 `spring-boot-starter-web`（Servlet）
共存于同一应用，所以每个服务必须独立启动。

## ✍️ 动手练习

1. 启动两个 `user-service` 实例（改端口 8083），观察网关/Feign 的负载均衡轮询。
2. 给网关增加全局过滤器（`GlobalFilter`），打印每个请求的路径和耗时。
3. 新增一个 `product-service`（端口 8083），在网关加路由，并让 order-service 通过 Feign 调用它。
4. 把 `eureka-server` 的 `enable-self-preservation` 改回 `true`，对比观察自我保护效果。
5. 在网关配置 `spring.cloud.gateway.routes[].filters` 增加 `AddRequestHeader`，验证透传。
