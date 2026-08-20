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
| TCP IM | `chat/ChatServer` + `ChatClient` | 多人实时聊天（完整项目） | ChannelGroup 广播、StringEncoder/Decoder、AttributeKey 属性 |
| HTTP | `http/HttpServer` + `http/HttpClient` | `/hello`、`/health` 和 404 路由 | HttpServerCodec、HttpObjectAggregator、Keep-Alive |
| UDP | `udp/UdpServer` + `udp/UdpClient` | 无连接数据报回显 | NioDatagramChannel、DatagramPacket、发送方地址 |
| WebSocket | `websocket/WebSocketServer` | HTTP Upgrade 后回显文本帧 | WebSocketServerProtocolHandler、WebSocketFrame |
| TLS/SSL | `ssl/SslServer` + `ssl/SslClient` | 自签名证书加密文本回显 | SslContext、SelfSignedCertificate、TLS Pipeline |

## 🚀 运行方式

```bash
# 测试（包含真实 TCP 回声集成测试）
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
# 客户端输入 'NICK:小明' 设置昵称，输入 'quit' 退出
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

## ✍️ 动手练习

1. 给 EchoServer 加上 `LineBasedFrameDecoder`，改成"按行回声"协议。
2. 心跳客户端把 `WRITER_IDLE_SECONDS` 改成 1 秒，观察服务端是否还断开（理解读/写空闲的区别）。
3. 群聊增加私聊功能：`@昵称 消息` 只发给指定用户（提示：遍历 CHANNELS 匹配昵称属性）。
4. 用 `LengthFieldBasedFrameDecoder` 设计一个带消息类型的协议（1 字节类型 + 4 字节长度 + 内容）。
5. 给心跳服务器加"连续 N 次没收到心跳才断开"的逻辑（用计数 + 定时重置）。
