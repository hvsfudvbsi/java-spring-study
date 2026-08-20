# module-16-spring-boot-netty · Spring Boot 集成 Netty

> 回答一个问题：**自己的服务如何利用 Netty 提升性能？是单独建服务，还是在 Tomcat 里也能用？**
> 本模块给出第二种（Tomcat 同 JVM 内嵌 Netty）的完整可运行示例；第一种（独立 Netty 进程）见 module-11-netty。

## 🎯 先给结论：两种模式怎么选

### 模式 A：独立 Netty 进程（module-11 的做法）

Netty 作为**单独的服务/进程**运行（如网关、IM、推送、代理），通过 HTTP/RPC/消息队列与业务服务通信。

```text
客户端 --TCP/WS--> Netty 进程（长连接、高并发） --RPC/HTTP--> 业务服务（Spring Boot/Tomcat）
```

| 优点 | 缺点 |
|------|------|
| 独立扩缩容（Netty 实例和业务服务分别加机器） | 多一个进程/部署单元，运维更复杂 |
| 故障隔离（Netty 挂了不影响业务，反之亦然） | 跨进程通信有序列化/网络开销 |
| Netty 线程和业务线程彻底分离 | 需要自己处理两个服务间的协调（如推送回调业务） |

### 模式 B：Tomcat 进程内嵌 Netty（本模块演示）

**同一个 Spring Boot 进程**里：内嵌 Tomcat 处理 HTTP/REST（8080），同时再启动一个 Netty TCP 服务（19090）处理长连接/高吞吐流量。

```text
客户端 --HTTP--> Tomcat（8080）─┐
                               ├── 同一个 JVM，共享 Spring Bean
客户端 --TCP---> Netty（19090）─┘
```

| 优点 | 缺点 |
|------|------|
| 共享 Spring Bean/数据库/事务：REST 接口直接读 Netty 状态（见 `/api/tcp-stats`） | 进程内共享资源，故障互相影响 |
| 部署仍是单个 jar，简单 | Netty 和业务一起扩缩容，无法单独调 |
| 长连接不占 Tomcat 请求线程（Netty EventLoop 独立线程池） | 进程内线程总量要一起评估 |

### ⚠️ 重要澄清：Tomcat 本身不用 Netty

- Tomcat 的 NIO 连接器是**它自己的 Java NIO 实现**，不是 Netty。想调 Tomcat 性能，用 `server.tomcat.*` 配置（线程池、acceptCount、maxConnections），示例写在 `application.properties` 里。
- "在 Tomcat 里利用 Netty" = **同 JVM 额外起一个 Netty 服务**（模式 B），而不是让 Tomcat 用 Netty 当连接器。
- 什么时候必须独立进程（模式 A）：连接数上万、长连接占主流（IM/推送/网关）、需要独立扩容或故障隔离时。

## 📖 本模块内容

| 文件 | 作用 |
|------|------|
| `SpringBootNettyApplication` | Spring Boot 入口（内嵌 Tomcat） |
| `WebController` | Tomcat REST：`/api/hello`、`/api/tcp-stats`（读 Netty 实时统计） |
| `NettyTcpServer` | `ApplicationRunner` 启动 Netty TCP 服务（默认 19090，可配）；`@PreDestroy` 优雅关闭 |
| `NettyTcpServerHandler` | Netty 业务 Handler：**Spring 单例（@Sharable）**，按行回显 `echo: xxx`，写入 `TcpStats` |
| `TcpStats` | 共享统计 Bean：在线连接数、累计消息数（Netty 写、Tomcat 读） |
| `TcpClientDemo` | 手动验证用的 TCP 客户端 |

### 同 JVM 嵌入的三个关键点

1. **Handler 是 Spring Bean**：`@Component + @ChannelHandler.Sharable`，所有连接共用一个实例；跨连接状态（在线数/消息数）放线程安全的 `TcpStats`，不能放 Handler 字段。
2. **生命周期归 Spring**：`ApplicationRunner.run()` 在容器就绪后启动 Netty；`@PreDestroy` 在应用关闭时停 Netty（测试结束也会触发）。
3. **端口与线程独立**：Netty 用 `netty.server.port`（默认 19090，`0` = 随机端口便于测试）；EventLoop 线程与 Tomcat 线程池互不干扰。

## 🚀 运行方式

```bash
# 1. 启动应用（Tomcat 8080 + Netty 19090）
mvn spring-boot:run -pl module-16-spring-boot-netty

# 2. 验证 Tomcat REST（另一终端）
curl http://127.0.0.1:8080/api/hello
curl http://127.0.0.1:8080/api/tcp-stats

# 3. 验证 Netty TCP（另一终端，二选一）
printf 'hello\nworld\n' | nc 127.0.0.1 19090
# 或运行模块自带的客户端
mvn compile exec:java -pl module-16-spring-boot-netty -Dexec.mainClass=com.study.nettyboot.TcpClientDemo

# 4. 再访问 /api/tcp-stats，能看到 totalMessages 增加 —— 这就是 Tomcat 读到 Netty 的实时状态
```

预期输出（TCP）：`welcome, 当前在线 1` → `echo: hello` → `echo: world`。
`/api/tcp-stats` 示例：`{"activeConnections":1,"nettyTcpPort":19090,"totalMessages":2}`。

## 🧪 测试

`SpringBootNettyIntegrationTest`（`@SpringBootTest`，Tomcat 随机端口 + Netty `port=0` 随机端口）：
- Tomcat REST `/api/hello` 返回 200；
- Netty TCP：连接收到欢迎、发 3 行收到 3 条回声、断开后在线数归零；
- 共享 Bean：REST `/api/tcp-stats` 能读到 Netty 的实时连接数和消息数。

```bash
mvn test -pl module-16-spring-boot-netty
```

## 🧯 常见问题

1. **端口被占用**：改 `application.properties` 里的 `server.port` / `netty.server.port`。
2. **Netty 起不来**：确认 `netty.server.workers` 等属性行内不要有 `#` 注释（properties 格式里 `#` 只在行首才是注释）。
3. **Handler 状态串了**：`@Sharable` 单例不能存每连接状态，用 `TcpStats` 这类线程安全 Bean 或 `AttributeKey`。
4. **想要独立扩缩容/上万连接**：考虑模式 A，把 Netty 拆成独立进程（写法见 module-11-netty 的 EchoServer/ChatServer）。
