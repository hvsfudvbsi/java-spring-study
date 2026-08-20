# module-15-network · 计算机网络与原生网络编程

> 纯 Java 模块（不依赖 Spring/Netty）。从网络分层模型到 TCP/UDP 报文格式逐位解析，再到 JDK 原生 Socket 编程。
> 用最小代码把"面试必问的计算机网络"变成可运行、可测试的实验。

## 📖 本模块内容

### 第一部分：报文格式（逐字段、逐位解析）

| 类 | 覆盖内容 | 首部长度 |
|----|---------|---------|
| `packet/EthernetFrame` | 数据链路层帧头：目的/源 MAC（各 6 字节）、EtherType（2 字节） | 14 字节固定 |
| `packet/IpHeader` | 网络层 IPv4 首部：版本(4 bit) + IHL(4 bit) 挤同一字节、总长度、TTL、协议号、源/目的 IP | 20 字节最小（IHL×4） |
| `packet/TcpHeader` | 传输层 TCP 首部：源/目的端口、序号、确认号、**数据偏移(4 bit)+标志位(9 bit)**、窗口 | 20 字节最小 |
| `packet/UdpHeader` | 传输层 UDP 首部：源/目的端口、长度、校验和 | 8 字节固定 |
| `packet/IcmpHeader` | 网络层 ICMP 首部：**类型/代码/校验和**/标识/序号（ping 的报文） | 8 字节固定 |
| `packet/PacketParser` | 完整报文分层解析：以太网 → IP → TCP/UDP/ICMP → 负载（模拟 Wireshark 逐层剥离） | — |

**首部长度速记**：以太网 14 < UDP 8？不——UDP 8 字节是首部，以太网 14 字节是帧头，两者不同层。同层对比：**TCP 20+ vs UDP 8**。

### 第二部分：TCP vs UDP 对比（面试必问）

`protocol/TransportProtocol` 枚举 + `printComparison()` 输出完整对比表：

| 特性 | TCP | UDP |
|------|-----|-----|
| 连接方式 | 面向连接：三次握手、四次挥手 | 无连接：发完即走 |
| 可靠性 | 可靠：确认/重传/排序/去重/流量/拥塞控制 | 不可靠：丢包不重传 |
| 有序性 | 有序交付 | 可能乱序 |
| 消息边界 | **字节流**：无边界（粘包拆包问题） | **数据报**：一次 send 对应一次 receive |
| 首部开销 | 最少 20 字节 + 选项 | 固定 8 字节 |
| 典型应用 | HTTP/HTTPS、FTP、SMTP、数据库 | DNS、RTP 音视频、DHCP、游戏、NTP |

### 第三部分：JDK 原生 Socket 编程

| 类 | 内容 |
|----|------|
| `socket/TcpEchoServer` + `TcpEchoClient` | TCP 回显：ServerSocket.accept → Socket 读写（三次握手/四次挥手） |
| `socket/UdpEchoServer` + `UdpEchoClient` | UDP 回显：DatagramSocket.receive/send（无连接） |
| `socket/TcpStickyPacketDemo` | **粘包演示**：连续发 3 条消息，接收方 read 次数 < 3（无边界）vs UDP 正好 3 次（有边界） |
| `socket/framed/FrameCodec` | **长度头帧协议**：`[4 字节长度][UTF-8 内容]`，编码 + 累积解码（粘包/拆包/半帧） |
| `socket/framed/FramedTcpServer` + `FramedTcpClient` | **多线程帧协议服务器**：每连接一线程，按长度头拆帧回声 |
| `tls/TlsHandshakeDemo` | **纯 JDK TLS 握手详解**：SSLSocket 真实握手，打印 ClientHello→Finished 报文与协商结果 | javax.net.debug 跟踪、SSLContext/KeyStore/自签名证书、SSLSession |

## 🚀 运行方式

```bash
# 测试（报文解析 + TCP/UDP 回环集成测试）
mvn test -pl module-15-network

# 运行全部演示（对比表 + 报文解析 + 粘包演示）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.Main

# 单独运行 TCP 回显（两个终端）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.TcpEchoServer
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.TcpEchoClient

# 单独运行 UDP 回显（两个终端）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.UdpEchoServer
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.UdpEchoClient

# 亲眼观察 TLS 握手（单进程内自动启动 SSLSocket 服务端+客户端）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.tls.TlsHandshakeDemo
```

## 🔍 核心概念讲解

### 1. 网络分层模型

数据从应用层到物理层逐层**封装**（加首部），接收方逐层**解封装**（去首部）：

```text
发送方:  应用数据 -> [TCP/UDP 首部] -> [IP 首部] -> [以太网帧头] -> 网线
接收方:  网线 -> 去掉以太网帧头 -> 去掉 IP 首部 -> 去掉 TCP/UDP 首部 -> 应用数据
```

| 层 | 典型协议 | 首部/格式 |
|----|---------|----------|
| 应用层 | HTTP、DNS、FTP | 业务数据 |
| 传输层 | TCP（20B+）、UDP（8B） | 端口号标识进程 |
| 网络层 | IP（20B+） | IP 地址端到端寻址 |
| 数据链路层 | 以太网（14B 帧头） | MAC 地址逐跳寻址 |

关键区别：**MAC 地址每跳都变，IP 地址端到端不变**。

### 2. TCP 首部位字段（为什么有"首部长度"）

```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         源端口 (16)           |       目的端口 (16)            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        序号 (32)                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       确认号 (32)                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| 数据偏移(4) | 保留(3) |N|C|E|U|A|P|R|S|F|    窗口大小 (16)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      校验和 (16)              |       紧急指针 (16)            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

- **数据偏移（4 bit）**：TCP 首部长度 ÷ 4。最小 5 → 20 字节；带选项时更大。因为只有 4 bit 表示"多少个 4 字节"，所以叫**首部长度字段**。
- **标志位（9 bit）**：URG/ACK/PSH/RST/SYN/FIN 等。三次握手靠 SYN/ACK，四次挥手靠 FIN/ACK。
- **窗口（16 bit）**：接收方剩余缓冲区，实现流量控制。

`TcpHeader` 演示了这些字段的编码（`encode`）与按位解析（`parse`），比如 `dataOffset=6 → 第 12 字节 = 0x60`。

### 3. 为什么 TCP 有粘包、UDP 没有

- **TCP 是字节流**：内核把数据拼成连续的字节，不保留应用层的"消息"边界。发送方 `write` 3 次，接收方 `read` 可能 1 次读到全部（粘包）、也可能 1 条被拆成多次（拆包）。
- **UDP 是数据报**：内核按数据报为单位收发，一次 `send` 对应一次 `receive`，天然有边界。

所以 TCP 应用层必须自定义帧格式（定长/分隔符/长度字段）。本模块的 `FrameCodec` 实现了最通用的**长度头方案**：

```text
发送:  String -> [4 字节长度][UTF-8 内容]
接收:  累积字节流 -> 长度头不足等 4 字节 -> 内容不足等补齐 -> 完整帧产出
```

`FrameDecoder` 累积缓冲正确处理粘包（一批多帧一次产出）、拆包（半帧等待）、长度头被拆（等够 4 字节）。这正是 Netty `LengthFieldBasedFrameDecoder` 的思路（见 module-11-netty）。### 4. ICMP：网络层的控制报文（ping 就是它）

ICMP 首部 8 字节：`[类型(8)][代码(8)][校验和(16)][标识(16)][序号(16)]`。

| 类型 | 含义 | 典型场景 |
|------|------|---------|
| 0 | Echo Reply（回显应答） | ping 成功返回 |
| 3 | Destination Unreachable | 目的不可达 |
| 8 | Echo Request（回显请求） | ping 发起 |
| 11 | Time Exceeded（超时） | TTL 耗尽，traceroute 利用它 |

关键理解：ICMP 封装在 IP 数据报里（IP 协议号 = 1），但它**不是传输层协议**——没有端口号、不承载应用数据，只是网络层的控制/诊断报文。`PacketParser` 按协议号分派：1=ICMP、6=TCP、17=UDP。

### 5. 三次握手与四次挥手（配合首部理解）

```text
三次握手（建立连接）:  Client -> SYN        Server
                      Client <- SYN+ACK    Server
                      Client -> ACK         Server

四次挥手（释放连接）:  Client -> FIN        Server
                      Client <- ACK        Server
                      Client <- FIN        Server
                      Client -> ACK         Server
```

**状态机视角**（`protocol/TcpStateMachine`，RFC 793 全部 11 个状态）：

```text
三次握手:  客户端 CLOSED -> SYN_SENT -> ESTABLISHED
          服务端 CLOSED -> LISTEN -> SYN_RECEIVED -> ESTABLISHED

四次挥手:  主动方 ESTABLISHED -> FIN_WAIT_1 -> FIN_WAIT_2 -> TIME_WAIT -> CLOSED
          被动方 ESTABLISHED -> CLOSE_WAIT -> LAST_ACK -> CLOSED

同时关闭:  FIN_WAIT_1 -> CLOSING -> TIME_WAIT -> CLOSED
```

关键理解：
- **TIME_WAIT 只属于主动关闭方**：等待 2MSL（2 倍报文最大生存时间）确保最后一个 ACK 到达，然后才真正 CLOSED。
- **被动关闭方没有 FIN_WAIT/TIME_WAIT**：收到 FIN 回 ACK 后进入 CLOSE_WAIT，等应用层关闭后发 FIN 进 LAST_ACK，收到 ACK 即 CLOSED。
- 状态机拒绝非法转换：如 ESTABLISHED 再收 SYN、CLOSED 直接收 FIN 都会抛 `IllegalStateException`。

本模块的 `TcpHeader` 可构造 SYN/FIN/ACK 报文观察标志位，`TcpStateMachine` 可模拟完整状态流转；真实抓包用 `tcpdump`/Wireshark（或 `curl -v`）。

### 6. TLS 握手：应用层加密通道的建立（对应 `tls/TlsHandshakeDemo`）

HTTPS 就是在 TCP 之上先做一次 TLS 握手、再加密传输。握手是客户端与服务端**协商参数 + 互相证明身份**的过程，运行 `TlsHandshakeDemo`（纯 JDK `SSLSocket`）会开启 JSSE 握手跟踪（`javax.net.debug=ssl:handshake`），打印 TLS 1.3 每一步的真实报文：

| 步骤 | 方向 | 作用 |
|------|------|------|
| ClientHello | 客户端→服务器 | 客户端随机数、支持的 TLS 版本与密码套件、SNI（服务器名） |
| ServerHello | 服务器→客户端 | 选定 TLS 版本与密码套件，返回服务器随机数 |
| EncryptedExtensions | 服务器→客户端 | 此后传输加密；传递扩展参数 |
| Certificate | 服务器→客户端 | 服务器证书链，客户端验证身份（本示例信任所有证书） |
| CertificateVerify | 服务器→客户端 | 私钥签名，证明证书与私钥匹配、握手中途未被篡改 |
| Finished | 服务器→客户端 | 对全部握手消息的完整性校验值 |
| Finished | 客户端→服务器 | 客户端同样发送校验值，双向确认 |
| Application Data | 双向 | 业务数据用协商出的密钥加密传输 |

关键理解：
- **协商**：版本、密码套件、随机数、密钥参数在 ClientHello/ServerHello 中定下来，之后的密钥交换在加密扩展里完成；
- **身份认证**：Certificate 携带证书链，CertificateVerify 用私钥签名证明持证，防止中间人；
- **双向确认**：双方各发一次 Finished 校验全部握手消息，任何一方中途被篡改都会失败。
- 想看密钥细节，把调试级别改成 `ssl:handshake:verbose`。Netty 版演示见 [module-11-netty](../module-11-netty) 的 `ssl/SslHandshakeDemo`。

## 🧪 测试

| 测试类 | 验证内容 | 不验证的内容 |
|--------|----------|--------------|
| `TcpHeaderTest`（6） | 编码/解析往返、SYN/ACK/FIN 标志位、数据偏移决定首部长度、标志位字节位置 | 真实网络行为 |
| `UdpHeaderTest`（3） | 8 字节固定、大端字节序、负载长度计算 | 丢包/乱序 |
| `IpHeaderTest`（5） | 版本+IHL 位字段、点分十进制互转、IP 每段 0~255 校验 | 路由/分片 |
| `EthernetFrameTest`（4） | 14 字节帧头、MAC 地址转换、EtherType | 真实网卡 |
| `IcmpHeaderTest`（6） | ping 请求/回复往返、字段位置、类型名称、偏移解析 | 真实 ping 抓包 |
| `PacketParserTest`（4） | 完整报文 TCP/UDP/**ICMP** 分层解析、未知协议拒绝 | 真实抓包 |
| `TransportProtocolTest`（4） | TCP/UDP 属性与首部长度对比、协议号反查 | — |
| `TcpStateMachineTest`（12） | 三次握手、主动/被动四次挥手、TIME_WAIT 归属、同时关闭、非法转换拒绝 | 真实网络时序 |
| `SocketIntegrationTest`（4） | **真实回环** TCP/UDP 回显、粘包 vs 有边界 | 跨主机网络 |
| `FrameCodecTest`（9） | 长度头编码、粘包多帧、拆包等待、长度头分批、非法超长拒绝 | 真实网络 |
| `FramedTcpServerIntegrationTest`（4） | **真实回环**多帧回声、特殊字符、双客户端并发、跨 TCP 分段拼帧 | 跨主机网络 |
| `TlsHandshakeDemoTest`（1） | **真实回环** SSLSocket 握手成功、协商协议/密码套件、收到回显 | 正式证书链、主机名校验 |

> 共 61 个测试，全部带 `@DisplayName`。Socket 测试用随机端口，不依赖固定端口。

## 🧯 常见问题排查

1. **端口被占用**：关闭旧进程或换端口（测试用随机端口避免冲突）。
2. **TCP 读到半条消息**：这是字节流正常现象，加帧解码器（见 module-11）。
3. **UDP 收不到回显**：UDP 无连接，先确认服务端已启动、地址端口一致；数据报可能被防火墙丢弃。
4. **IP 校验失败**：确认每段在 0~255，段数必须为 4。
5. **TCP 粘包演示不稳定**：回环网络下 read 次数可能为 1 或 2，测试断言的是"次数 < 3 且数据完整"，两者都符合。

## ✍️ 动手练习

1. 给 `TcpHeader` 增加 `URG` 标志位支持（0x20），补一个 URG 报文往返测试。
2. 用 `PacketParser` 构造一个"IP + UDP + DNS 查询"报文，断言解析出 `destinationPort=53`。
3. 给 `FramedTcpServer` 增加协议版本号：帧头改为 `[1 字节版本][4 字节长度][内容]`，不匹配的版本直接断开（提示：改 `FrameCodec.encode/decode` 并补测试）。
4. 用 `tcpdump -i lo port 19001` 抓包观察三次握手，对照 `TcpHeader` 的标志位。
5. 给 `TcpStateMachine` 增加 `RST` 事件（连接重置）：ESTABLISHED + RST → 直接 CLOSED，补测试。
6. 给 `IcmpHeader` 增加校验和计算：`checksum = 反码和(首部 + 数据)`，构造合法校验和并验证往返一致。

## 📄 关联模块

- [module-11-netty](../module-11-netty)：Netty 对 TCP 粘包/拆包的工业级解决（解码器、心跳、群聊）；TLS 部分有 Netty 版握手演示 `ssl/SslHandshakeDemo`，与本模块的纯 JDK 版 `tls/TlsHandshakeDemo` 相互对照。
- [module-12-multithreading](../module-12-multithreading)：Socket 服务器多线程处理的并发基础。
