# module-11-netty · Netty 网络编程

> 纯 Java 模块（不依赖 Spring）。从 API 方法用例到 TCP、UDP、HTTP、WebSocket、TLS、心跳和 IM 群聊等网络协议实操。
> Netty 4.1.137.Final。

## 📖 本模块内容

### 第一部分：API 方法用例（常用 + 不常用）

| 类 | 覆盖的 API |
|----|-----------|
| `apidemo/ByteBufApiDemo` | **常用**：write*/read*、readableBytes、retain/release、copy、slice、clear；**不常用**：markReaderIndex/reset、getByte/setByte 绝对定位、skipBytes、discardReadBytes、ensureWritable、indexOf、bytesBefore、forEachByte、readUnsignedInt、writeZero、nioBuffer、hasArray/isDirect、unwrap |
| `apidemo/ChannelApiDemo` | **常用**：writeInbound/writeOutbound、isActive/isOpen、pipeline/eventLoop/alloc、localAddress/remoteAddress、close；**不常用**：write+flush 分离、setAutoRead/read 手动流量控制、isWritable/bytesBeforeUnwritable、id/metadata/parent、runPendingTasks/checkException/finish |
| `apidemo/EventLoopApiDemo` | **常用**：execute、submit、schedule、scheduleAtFixedRate、shutdownGracefully；**不常用**：scheduleWithFixedDelay、inEventLoop、parent/next、awaitTermination、isShuttingDown |
| `apidemo/FutureApiDemo` | **常用**：addListener 回调、sync、isSuccess、cause、getNow；**不常用**：await(超时)、awaitUninterruptibly、removeListener、cancel/isCancelled、syncUninterruptibly、Future.allOf、map 转换 |
| `apidemo/PipelineApiDemo` | **常用**：addLast/addFirst/addBefore/addAfter、fireChannelRead、context、first/last；**不常用**：remove(3 种重载)、replace、names/toMap、firstContext/lastContext、fireChannelActive/ExceptionCaught/UserEventTriggered |
| `apidemo/BootstrapApiDemo` | **常用**：group/channel/handler、childHandler、bind、connect；**不常用**：handler(boss)、option/childOption、attr/childAttr、localAddress/remoteAddress、validate、回调式 connect |
| `codec/FrameDecoderDemo` | 粘包拆包四种解码器：LineBased / DelimiterBased / FixedLength / LengthFieldBased+Prepender |
| `codec/CustomCodecDemo` | 自定义编解码器：MessageToByteEncoder / ByteToMessageDecoder、markReaderIndex 回退处理半帧 |

### 第二部分：网络实操示例

| 实操 | 文件 | 功能 | 技术点 |
|------|------|------|--------|
| TCP 回声 | `echo/EchoServer` + `echo/EchoClient` | 收到什么回什么 | NIO TCP 服务端/客户端完整启动流程 |
| TCP 心跳 | `heartbeat/HeartbeatServer` + `HeartbeatClient` | 检测假死连接、定时发送 PING | IdleStateHandler、userEventTriggered |
| TCP IM | `chat/ChatServer` + `ChatClient` | 多人实时聊天（完整项目），支持昵称与 `@昵称` 私聊 | ChannelGroup 广播、StringEncoder/Decoder、AttributeKey 属性、私聊定向发送 |
| HTTP | `http/HttpServer` + `http/HttpClient` | `/hello`、`/health` 和 404 路由 | HttpServerCodec、HttpObjectAggregator、Keep-Alive |
| UDP | `udp/UdpServer` + `udp/UdpClient` | 无连接数据报回显 | NioDatagramChannel、DatagramPacket、发送方地址 |
| WebSocket | `websocket/WebSocketServer` | HTTP Upgrade 后处理文本、二进制和 Ping/Pong 帧 | WebSocketServerProtocolHandler、WebSocketFrame、协议心跳 |
| TLS/SSL | `ssl/SslServer` + `ssl/SslClient` | 自签名证书加密文本回显 | SslContext、SelfSignedCertificate、TLS Pipeline |
| TLS 握手观察 | `ssl/SslHandshakeDemo` | 打印 ClientHello→Finished 每一步报文与协商结果 | javax.net.debug 握手跟踪、handshakeFuture、SSLSession |

## 🚀 运行方式

```bash
# 测试（包含 EmbeddedChannel、真实 TCP 回声、HTTP/UDP 随机端口和 TLS 启动测试）
mvn test -pl module-11-netty

# 运行所有 API 方法用例（无需网络）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.Main
```

### 实操一：回声
```bash
# 终端 1
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.echo.EchoServer
# 终端 2
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.echo.EchoClient
```

### 实操二：心跳
```bash
# 终端 1：心跳服务器（18081）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.heartbeat.HeartbeatServer
# 终端 2：心跳客户端（每 3 秒发 PING）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.heartbeat.HeartbeatClient
# 验证假死清理：直接 Ctrl+C 强杀客户端，观察服务端 5 秒后断开该连接
```

### 实操三：群聊
```bash
# 终端 1：群聊服务器（18082）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.chat.ChatServer
# 终端 2/3：两个群聊客户端（IDEA 中可开多个实例，实现多人聊天）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.chat.ChatClient
# 客户端输入 'NICK:小明' 设置昵称，输入 '@小红 你好' 私聊（只发给小红），输入 'quit' 退出
```

### 实操四：HTTP
```bash
# 终端 1：HTTP 服务端（18083）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.http.HttpServer
# 终端 2：Netty HTTP 客户端
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.http.HttpClient
# 也可以访问：curl http://127.0.0.1:18083/health
```

### 实操五：UDP
```bash
# 终端 1：UDP 服务端（18084）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.udp.UdpServer
# 终端 2：UDP 客户端
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.udp.UdpClient
```

### 实操六：WebSocket
```bash
# 启动服务端（18085，路径 /ws）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.websocket.WebSocketServer
# 浏览器控制台连接并发送消息
const socket = new WebSocket('ws://127.0.0.1:18085/ws');
socket.onmessage = event => console.log(event.data);
socket.onopen = () => socket.send('hello websocket');
```

### 实操七：TLS/SSL
```bash
# 终端 1：TLS 服务端（18086，启动时生成临时自签名证书）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.ssl.SslServer
# 终端 2：TLS 客户端（学习示例会信任所有证书）
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.ssl.SslClient
```

### 实操八：亲眼观察 TLS 握手
```bash
# 单进程内自动启动 TLS 服务端+客户端，打印 ClientHello→Finished 每一步报文
mvn compile exec:java -pl module-11-netty -Dexec.mainClass=com.study.netty.ssl.SslHandshakeDemo
```

> HTTP、UDP、WebSocket 和 TLS 的处理器均有 `EmbeddedChannel`/本地随机端口测试；测试不依赖固定端口，执行 `mvn test -pl module-11-netty` 即可验证。

## 🔍 核心概念讲解（面试必问）

### 1. 线程模型
```
EventLoopGroup（线程池）
  └── EventLoop（线程，如 2 个）
        └── 每个 EventLoop 绑定多个 Channel
```
- 一个 Channel 的所有 IO 都在**同一个 EventLoop 线程**执行 → 无锁设计
- 不要在 handler 中做耗时操作（会阻塞该线程上的所有连接）
- 跨线程操作用 `channel.eventLoop().execute(...)`

### 2. 责任链（Pipeline）
```
入站（读）: head -> 解码器 -> 业务Handler -> tail
出站（写）: tail -> 业务Handler -> 编码器 -> head
```
- 解码器加在**靠前**，编码器加在**靠后**
- `ctx.fireChannelRead()` 传递给下一个 handler

### 3. 粘包拆包
TCP 是流协议，无消息边界 → 需要协议层解决：
| 方案 | 适用 |
|------|------|
| 定长消息 | 固定长度报文 |
| 分隔符 | 文本协议（`\n`、`;`） |
| 长度字段 | 二进制协议（最通用） |
| 自定义解码器 | 复杂协议（markReaderIndex 回退处理半帧） |

### 4. 内存管理
- ByteBuf 用**引用计数**：`retain()/release()` 成对使用，池化缓冲必须释放
- `slice()/duplicate()` 零拷贝共享内存；`copy()` 深拷贝

## 🧭 知识点完整学习路线

### 1. ByteBuf：Netty 的字节容器

`ByteBuf` 不是简单的 `byte[]` 包装器，而是一个带有两个指针和引用计数的缓冲区：

```text
capacity
|------------------------------------------------|
0       readerIndex       writerIndex       capacity
        <--- readable ---> <--- writable ------>
```

- `readerIndex` 指向下一次读取的位置；`readInt()`、`readBytes()` 会移动它。
- `writerIndex` 指向下一次写入的位置；`writeInt()`、`writeBytes()` 会移动它。
- `readableBytes = writerIndex - readerIndex`，表示当前可以交给业务读取的数据。
- `writableBytes = capacity - writerIndex`，不足时 Netty 可能自动扩容到 `maxCapacity`。
- `get/set` 是绝对定位读写，不移动指针；`read/write` 是相对定位读写，会移动指针。
- `clear()` 只把两个指针复位，不会擦除底层字节；`discardReadBytes()` 会移动未读数据，可能产生复制成本。

#### 堆内存、直接内存和池化

- 堆内存容易调试，可以通过 `array()` 访问数组；直接内存适合 Socket I/O，但不能直接访问普通 Java 数组。
- `ByteBufAllocator` 负责分配缓冲区；生产代码优先使用 `ctx.alloc()`，让 Netty 统一管理池化内存。
- `slice()`、`duplicate()` 是共享底层内存的视图，修改一方可能影响另一方；需要完全隔离时使用 `copy()`。
- Netty 使用引用计数管理部分缓冲区。谁创建、谁转交、谁消费必须明确所有权；继承 `SimpleChannelInboundHandler` 时通常会自动释放，继承 `ChannelInboundHandlerAdapter` 时通常要手动 `release()`。

源码：`apidemo/ByteBufApiDemo`；测试：`ByteBufApiTest`。

### 2. Pipeline：入站和出站是两个方向

每个 Channel 都有一个双向 Pipeline：

```text
入站字节 -> HttpServerCodec/Decoder -> Aggregator -> 业务 Handler
业务 Handler -> Encoder/Codec -> 出站字节
```

- 入站事件从 `head` 向 `tail` 传播；`ChannelInboundHandler` 处理读、连接建立和用户事件。
- 出站事件从 `tail` 向 `head` 传播；`ChannelOutboundHandler` 处理写、连接和刷新。
- `ctx.fireChannelRead(msg)` 从当前 Handler 继续向后传播；`pipeline.fireChannelRead(msg)` 从 Pipeline 头部开始传播。
- 解码器通常放在业务 Handler 前面；编码器必须放在业务 Handler 能够经过的出站路径上。
- `ChannelHandlerContext.writeAndFlush()` 和 `channel.writeAndFlush()` 的起始位置不同，设计自定义 Pipeline 时要特别注意。

源码：`apidemo/PipelineApiDemo`；测试：`ProtocolHandlerTest`。

### 3. Codec：TCP 没有消息边界

TCP 只保证字节有序到达，不保证一次写对应一次读，因此必须在应用层定义帧格式：

| 方案 | 帧边界 | 优点 | 风险/适用限制 |
|------|--------|------|---------------|
| 换行/分隔符 | `\\n`、`;` 等 | 文本协议直观 | 内容不能随意包含分隔符，必须转义 |
| 固定长度 | 每帧固定 N 字节 | 解析简单、速度稳定 | 浪费空间，不适合长度变化大的内容 |
| 长度字段 | 头部记录正文长度 | 二进制协议通用 | 要校验最大长度，避免恶意超大分配 |
| 自定义解码器 | 业务自定义 | 灵活 | 必须正确处理半包、粘包、非法长度和资源释放 |

`ByteToMessageDecoder` 的关键规则：

1. 先判断长度头是否完整；不完整时直接返回，等待下一批字节。
2. 读取长度头后判断正文是否完整；不完整时用 `markReaderIndex/resetReaderIndex` 回退。
3. 完整帧加入 `out`，一次 `decode` 可以循环产出多帧。
4. 对长度设置上限，并拒绝负数或异常长度，防止内存攻击。

源码：`codec/FrameDecoderDemo`、`codec/CustomCodecDemo`；测试：`CodecTest`。

### 4. TCP：连接、事件循环和优雅关闭

TCP 服务端通常有两个 EventLoopGroup：

```text
bossGroup：接收 accept，负责监听端口
workerGroup：处理已建立连接的读写和 Pipeline 事件
```

启动过程是：`ServerBootstrap.group` 配置线程组 → `channel(NioServerSocketChannel.class)` 指定监听通道 → `childHandler` 配置每个客户端连接的 Pipeline → `bind(port)` 监听端口。客户端使用 `Bootstrap.group`、`channel(NioSocketChannel.class)`、`handler` 和 `connect`。

关闭时要关闭服务端 Channel，并调用 `shutdownGracefully()` 停止 EventLoopGroup；否则 EventLoop 的非守护线程可能阻止 JVM 退出。源码：`echo/EchoServer`、`echo/EchoClient`。

### 5. 心跳：传输层连接不等于业务层存活

TCP 连接处于 `ESTABLISHED` 不代表对端应用仍然可用。心跳示例分成两层：

1. 客户端用 `IdleStateHandler(0, 3, 0, TimeUnit.SECONDS)` 检测写空闲。
2. 写空闲时发送应用层 `PING`；服务端收到后回复 `PONG`。
3. 服务端用读空闲检测器观察是否长期没有任何客户端数据。
4. **连续 N 次读空闲都没收到心跳才判定假死**：每次收到数据重置计数，读空闲时 +1，达到阈值（本示例 3 次 ≈ 15 秒）才关闭连接并释放资源。这样单次丢包/网络抖动不会误杀正常连接。

`IdleStateHandler` 只负责产生事件，不会自动发送 PING；真正的心跳协议、重试次数、超时策略必须由业务 Handler 定义（本示例的"连续 N 次漏心跳"计数就在 `HeartbeatServerHandler` 里）。源码：`heartbeat/HeartbeatServer`、`heartbeat/HeartbeatClient`；测试：`ProtocolHandlerTest`。

### 6. HTTP：先解码协议，再聚合请求

HTTP 服务端 Pipeline 的执行顺序是：

```text
HttpServerCodec -> HttpObjectAggregator -> HttpServerHandler
```

- `HttpServerCodec` 把字节解码为 HTTP 请求对象，也把响应对象编码为字节。
- HTTP 请求可能由多个 HttpContent 分段到达；`HttpObjectAggregator` 将其聚合成 `FullHttpRequest`，简化业务处理。
- 业务 Handler 根据 URI、方法和查询参数选择路由，设置状态码、Content-Type、Content-Length 和 Connection。
- `Keep-Alive` 会复用连接；非 Keep-Alive 响应发送后可以关闭连接。
- 聚合器必须设置最大长度，避免客户端发送超大请求耗尽内存。

源码：`http/HttpServer`、`http/HttpClient`、`http/HttpServerHandler`；测试：HTTP EmbeddedChannel 和随机端口链路测试。

### 7. UDP：无连接数据报

UDP 没有 TCP 的连接、重传、顺序和拥塞控制。Netty 使用 `NioDatagramChannel`，每次消息都是带地址的 `DatagramPacket`：

- `packet.content()` 是数据内容；`packet.sender()` 是发送方地址；`packet.recipient()` 是目标地址。
- 服务端收到数据报后可以直接向 `packet.sender()` 回包。
- 单个数据报应控制大小；过大的 UDP 包可能被 IP 分片或丢弃。
- 业务如果需要可靠性，必须自行设计序号、确认、重传、去重和超时。

源码：`udp/UdpServer`、`udp/UdpClient`；测试：`ProtocolHandlerTest` 和随机 UDP 端口链路测试。

### 8. WebSocket：HTTP Upgrade 后的长连接

WebSocket 不是直接替代 HTTP，而是先通过 HTTP Upgrade 完成握手，再在同一条 TCP 连接上交换帧：

1. 客户端发送带 `Upgrade: websocket` 的 HTTP 请求。
2. `WebSocketServerProtocolHandler` 校验路径并完成握手。
3. 握手成功后，后续数据不再按普通 HTTP 请求处理，而是 `WebSocketFrame`。
4. `WebSocketFrameHandler` 根据帧类型处理文本、二进制、Ping/Pong 和 Close。

当前示例展示文本帧回显、二进制帧回显和 Ping/Pong 协议心跳；`WebSocketServerProtocolHandler` 负责 Upgrade 和基础关闭流程。生产代码还需要限制帧大小、鉴权、Origin 校验、连接空闲超时和业务心跳。源码：`websocket/WebSocketServer`、`websocket/WebSocketFrameHandler`；浏览器连接方式见运行章节。

### 9. SSL/TLS：在 Pipeline 中加密 TCP

TLS Handler 必须放在业务协议 Handler 前面：

```text
原始 TCP 字节 -> SslHandler 解密 -> LineBasedFrameDecoder -> StringDecoder -> 业务 Handler
业务响应 -> StringEncoder -> SslHandler 加密 -> TCP 字节
```

- 服务端使用证书和私钥创建 `SslContext`；客户端使用信任管理器验证服务端证书。
- 本示例使用临时自签名证书和 `InsecureTrustManagerFactory`，只用于本地学习，不能用于生产。
- TLS 握手是异步的，业务数据要在握手完成后再发送；连接失败时要检查协议版本、证书、主机名和信任链。
- 生产环境必须使用真实证书、严格校验主机名，并妥善保护私钥。

源码：`ssl/SslServer`、`ssl/SslClient`；测试：`SslServerTest` 和 SSL 配置测试。

### 9.1 握手详细步骤：ClientHello 到 Finished（对应 `ssl/SslHandshakeDemo`）

TLS 握手是客户端与服务端先协商参数、再建立加密通道的过程。运行 `SslHandshakeDemo` 会开启
JSSE 握手跟踪（`javax.net.debug=ssl:handshake`），把 TLS 1.3 握手的每一步真实报文打印出来：

| 步骤 | 方向 | 作用 |
|------|------|------|
| ClientHello | 客户端→服务器 | 携带客户端随机数、支持的 TLS 版本与密码套件、SNI（服务器名） |
| ServerHello | 服务器→客户端 | 选定 TLS 版本与密码套件，返回服务器随机数 |
| EncryptedExtensions | 服务器→客户端 | 此后传输开始加密；传递扩展参数 |
| Certificate | 服务器→客户端 | 服务器证书链；客户端用它验证服务器身份（本示例信任所有证书） |
| CertificateVerify | 服务器→客户端 | 用私钥签名，证明证书与私钥匹配、握手中途未被篡改 |
| Finished | 服务器→客户端 | 对全部握手消息的完整性校验值 |
| Finished | 客户端→服务器 | 客户端同样发送校验值，双向确认 |
| Application Data | 双向 | 业务数据使用协商出的密钥加密传输 |

观察要点：
- 跟踪日志里 `ClientHello` / `ServerHello` 会出现双方协商的版本与密码套件；`Certificate`
  里能看到自签名证书（CN=localhost）；`Finished` 表示握手完成，此后所有日志里出现的
  `Application Data` 就是加密的业务数据。
- 想看密钥交换细节（随机数、密钥参数），把 `SslHandshakeDemo` 中的调试级别改成
  `ssl:handshake:verbose`。
- `SslServer`/`SslClient` 单独运行时，也会在控制台打印 `TLS 握手成功: 协议=...，密码套件=...，
  对端证书=...`，便于直接确认握手结果。

### 10. IM 群聊：ChannelGroup 与连接状态

群聊服务把在线 Channel 放进 `ChannelGroup`，每条消息遍历在线连接并排除发送者：

- `channelActive`：设置默认昵称、加入群组、发送欢迎消息。
- `channelRead0`：处理 `NICK:` 改名、`@昵称 内容` 私聊、`quit` 退出和普通广播消息。
- 私聊实现：解析 `@昵称 内容` 后**遍历在线用户匹配昵称属性**，只向目标 Channel 写入并给发送者回执；目标不在线或格式错误时提示发送者，不广播给其他人。
- 协议对称（按行传输）：客户端发送时每条消息带换行符，服务端用 `LineBasedFrameDecoder` 按行解码；服务端出站由 `ChatLineEncoder` 给每条消息补换行符，客户端同样按行解码——TCP 粘包时双方都能逐条还原消息。
- `channelInactive`：移出群组并广播离线通知。
- `AttributeKey` 把昵称附加到 Channel，避免把连接状态放进不安全的全局 Map；私聊正是通过遍历 `channels` 读取每个连接的昵称属性来定向。
- 测试 EmbeddedChannel 多连接时必须使用不同 ChannelId；默认 EmbeddedChannel 可能共享 ID，ChannelGroup 会把连接误认为同一个。

源码：`chat/ChatServer`、`chat/ChatServerHandler`、`chat/ChatClient`；测试：`ProtocolHandlerTest`。

### 11. 测试如何对应真实网络

| 测试方式 | 验证内容 | 不验证的内容 |
|----------|----------|--------------|
| `EmbeddedChannel` | Handler、编解码器、事件和引用计数 | 操作系统 Socket、真实网络延迟 |
| 随机 TCP/UDP 端口 | 服务端绑定、客户端连接、真实协议链路 | 生产网络、防火墙和负载均衡 |
| `SslServerTest` | SSL Context 和 Pipeline 能启动 | 真实证书链、主机名校验和生产安全配置 |
| `EchoIntegrationTest` | TCP 回声端到端结果 | 高并发性能和断网恢复 |

## 🧯 常见问题排查

1. **端口已占用**：关闭旧服务或修改示例端口；测试使用随机端口避免固定端口冲突。
2. **客户端连接失败**：确认服务端先启动、地址/端口一致，检查本机防火墙。
3. **读到半条消息**：TCP 是流，必须添加帧解码器，不能假设一次 read 就是一条业务消息。
4. **`IllegalReferenceCountException`**：检查 ByteBuf 是否重复 release，或是否在传递后继续访问已交给下一个 Handler 的对象。
5. **内存泄漏提示**：确认 `ChannelInboundHandlerAdapter` 手动释放 ByteBuf，测试中释放 `EmbeddedChannel` 的剩余消息。
6. **TLS 握手失败**：先确认服务端证书和客户端信任配置；学习示例的信任所有证书设置不能照搬到生产。
7. **IM 测试只有一个在线连接**：EmbeddedChannel 需要显式创建不同 ChannelId，不能依赖默认 ID。

## ✍️ 动手练习

1. 给 EchoServer 加上 `LineBasedFrameDecoder`，改成"按行回声"协议。
2. 心跳客户端把 `WRITER_IDLE_SECONDS` 改成 1 秒，观察服务端是否还断开（理解读/写空闲的区别）。
3. 给群聊增加"群文件/图片"消息类型（提示：定义 `FILE:昵称:文件名:内容` 协议，按类型分发）。
4. 用 `LengthFieldBasedFrameDecoder` 设计一个带消息类型的协议（1 字节类型 + 4 字节长度 + 内容）。
5. 让心跳服务器区分"普通数据"和"心跳"：只有收到 `PING` 才重置计数，普通消息不重置（当前实现是收到任何数据都重置）。
