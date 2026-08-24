# module-15-network · 计算机网络与原生网络编程

> 纯 Java 模块（不依赖 Spring/Netty）。从网络分层模型到 TCP/UDP 报文格式逐位解析，再到 JDK 原生 Socket 编程。
> 用最小代码把"面试必问的计算机网络"变成可运行、可测试的实验。

## 📖 本模块内容

### 第一部分：报文格式（逐字段、逐位解析）

| 类 | 覆盖内容 | 首部长度 |
|----|---------|---------|
| `packet/EthernetFrame` | 数据链路层帧头：目的/源 MAC（各 6 字节）、EtherType（2 字节） | 14 字节固定 |
| `packet/IpHeader` | 网络层 IPv4 首部：版本(4 bit) + IHL(4 bit) 挤同一字节、总长度、**分片三件套（标识/标志/片偏移）**、TTL、协议号、源/目的 IP | 20 字节最小（IHL×4） |
| `packet/TcpHeader` | 传输层 TCP 首部：源/目的端口、序号、确认号、**数据偏移(4 bit)+标志位(8 个)**、窗口、**伪首部校验和**、**选项字段** | 20 字节最小 |
| `packet/TcpOption` | TCP 选项：MSS/Window Scale/SACK-Permitted/时间戳 的构造、**NOP 对齐编码**与按 Kind/Length 解析 | 变长（最多 40 字节） |
| `packet/SackBlock` | **SACK 块**：乱序确认区间 `[leftEdge, rightEdge)`（各 4 字节大端），**32 位序号回绕处理**（模 2^32 的长度/包含判断），配合 `TcpOption.sack()` / `sackBlocks()` 编解码 | 8 字节/块 |
| `packet/UdpHeader` | 传输层 UDP 首部：源/目的端口、长度、**伪首部校验和（IPv4 可选）** | 8 字节固定 |
| `packet/Checksums` | **校验和工具（RFC 1071 反码和）**：IP 首部校验和、TCP/UDP 伪首部校验和、整体验证（反码和为 0xFFFF） | — |
| `packet/IcmpHeader` | 网络层 ICMP 首部：**类型/代码/校验和**/标识/序号（ping 的报文） | 8 字节固定 |
| `packet/ArpHeader` | **链路层 ARP 报文**：硬件/协议类型、地址长度、操作码（请求/回复）、发送方/目标 MAC+IP（以太网+IPv4 固定 28 字节） | 28 字节固定 |
| `packet/PacketParser` | 完整报文分层解析：**先按 EtherType 分派（0x0800=IPv4 / 0x0806=ARP）**，再按协议号分派 TCP/UDP/ICMP → 负载（模拟 Wireshark 逐层剥离） | — |
| `packet/DnsHeader` | 应用层 DNS 头部：事务 ID、标志（QR/Opcode/**AA/TC/RD/RA**/RCODE）、四类记录计数 | 12 字节固定 |
| `packet/DnsQuestion` | 应用层 DNS 查询记录：**QNAME 标签编码**（`[3]www[7]example[3]com[0]`）、QTYPE/QCLASS | 变长（域名 + 4） |
| `packet/HttpRequest` | 应用层 HTTP 请求：**请求行**（方法 URI 版本）+ 头部（**多值头**）+ 空行 + 请求体（Content-Length 校验） | 文本行（CRLF） |
| `packet/HttpResponse` | 应用层 HTTP 响应：**状态行**（版本 状态码 原因短语）+ 头部 + 空行 + 响应体，状态码语义 | 文本行（CRLF） |

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
| `socket/MinimalHttpClient` + `MinimalHttpServer` | **纯 JDK 最小 HTTP 客户端/服务器**：Socket 发请求收响应，按 Content-Length 或 **chunked 分块**切分响应（HTTP 版的粘包解决），HttpRequest/HttpResponse 真实落地 |
| `socket/HttpConnection` | **Keep-Alive 连接复用**：同一 Socket 连发多个请求，靠 Content-Length/chunked 切分响应边界；**管线化**（一次写出多请求依次读回）、**重定向跟随**（3xx+Location）、**gzip 自动解压**；Connection: close / HTTP/1.0 无长度 / EOF 时自动判定不可复用 |
| `tls/TlsHandshakeDemo` | **纯 JDK TLS 握手详解**：SSLSocket 真实握手，打印 ClientHello→Finished 报文与协商结果 | javax.net.debug 跟踪、SSLContext/KeyStore/自签名证书、SSLSession |

### 第四部分：IP 地址与子网划分（CIDR）

| 类 | 内容 |
|----|------|
| `ip/SubnetCalculator` | **CIDR 子网计算器**：前缀↔掩码互转、网络/广播地址、主机范围、可用主机数、归属判断（路由表匹配）、等分子网划分（如 /24 切成 4 个 /26） |

### 第五部分：TCP 拥塞控制

| 类 | 内容 |
|----|------|
| `protocol/TcpCongestionControl` | **TCP Reno 拥塞控制模拟**：慢启动（指数增长）、拥塞避免（线性增长）、超时（全盘重来）、快重传/快恢复（3 个重复 ACK）、有效窗口 min(cwnd, rwnd)、**ECN 响应（ECE→cwnd 减半→回 CWR）**、**SACK 精细化重传（只重传块空隙）** |

## 🚀 运行方式

```bash
# 测试（报文解析 + TCP/UDP 回环集成测试）
mvn test -pl module-15-network

# 运行全部演示（对比表 + 报文解析 + 粘包 + 子网划分 + 拥塞控制）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.Main

# 最小 HTTP 客户端/服务器（两个终端）：
# 终端 1 —— 启动服务器（默认 19080 端口）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.MinimalHttpServer
# 终端 2 —— 用客户端请求（也可直接访问公网：MinimalHttpClient www.example.com 80 /）
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.MinimalHttpClient -Dexec.args="127.0.0.1 19080 /hello"

# Keep-Alive 复用演示：一条连接连发 4 个请求（/、/hello、/chunked、/nope），服务端同一连接循环处理
mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.socket.HttpConnection -Dexec.args="127.0.0.1 19080"

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

### 2.1 TCP 选项：数据偏移装的是什么（对应 `packet/TcpOption`）

基础首部只够表达端口/序号/窗口，TCP 的很多能力必须**额外协商**，参数就放在选项里：
选项区域长度 = 数据偏移 × 4 − 20 字节，不足 4 的倍数用 NOP 填充。通用格式 `[Kind(1)][Length(1)][Value...]`（Length 含 Kind/Length 本身），EOL(0) 和 NOP(1) 是 1 字节特例。

| Kind | 选项 | 长度 | 作用（面试常问） |
|------|------|------|-----------------|
| 2 | **MSS** | 4 | 协商最大报文段，**SYN 里必带**，双方取小。1460 = 1500(MTU) − 20(IP) − 20(TCP) |
| 3 | Window Scale | 3 | 窗口左移 N 位放大（16 bit 不够用，最大可到 1GB） |
| 4 | SACK-Permitted | 2 | 声明支持选择性确认（丢包只重传丢失段，不用全部重传） |
| 5 | SACK | 变长 | 告知对端哪些段已收到（乱序到达时用），**每块 8 字节 = 左边界 4 + 右边界 4**（`SackBlock`） |
| 8 | Timestamp | 10 | 算 RTT、防序号回绕（PAWS） |

关键理解：
- **数据偏移的真正作用**：它不只为「首部长度」，更决定选项区域多大——解析时先读 dataOffset，才知道后面有多少选项字节。
- 带 MSS 的 SYN：选项 4 字节 → dataOffset=6 → 首部 24 字节（`TcpHeader.withOptions` 自动计算）。
- 选项参与 TCP 校验和（整个首部按实际长度算），篡改选项会被接收方发现。
- 选项区域最多 40 字节（dataOffset 4 bit 上限 15 → (15−5)×4）。

**SACK 块（`packet/SackBlock`，kind=5 的 Value 部分）——乱序确认区间：**

```text
一个块 8 字节: [左边界(4)][右边界(4)]    区间是右开的: [leftEdge, rightEdge)
左边界 = 块内第一个字节的序号；右边界 = 块内最后一个字节的序号 + 1
```

- **场景**：接收方收到 1000~2000 和 3000~4000（中间 2000~3000 丢了），普通 ACK 序号只能表达累计确认，无法描述「哪些不连续的区域到了」→ 用 SACK 块列表补上。发送方据此**只重传丢失的段**（快重传/快恢复的精细化，高带宽高延迟链路收益巨大）。
- **块的数量**：选项区域最多 40 字节，一个块 8 字节 → 最多 4 个块（2 + 4×8 = 34 字节）；第一个块是**最近收到的段**（含最大序号）。
- **序号回绕**：序号是 32 位循环的（4GB 后回绕），块边界按模 2^32 比较，允许跨回绕点的块（如 `[0xFFFFFFF0, 0x10)`，长度 32）。
- **面试常问**：SACK 只告诉发送方「哪些**到了**」，不告诉「哪些丢了」——丢失区间要靠对块之间的**空隙**推断（`SackBlock.gaps(sentStart, sentEnd, blocks)`：发送范围 − 已确认块 = 需要重传的段，排序 → 合并 → 求补集）；且 SACK 不能替代 ACK，累计确认仍由 ACK 序号负责（SACK 选项总是和 ACK 一起出现）。

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

ICMP 首部 8 字节：`[类型(8)][代码(8)][校验和(16)][标识(16)][序号(16)]`。

| 类型 | 含义 | 典型场景 |
|------|------|---------|
| 0 | Echo Reply（回显应答） | ping 成功返回 |
| 3 | Destination Unreachable | 目的不可达 |
| 8 | Echo Request（回显请求） | ping 发起 |
| 11 | Time Exceeded（超时） | TTL 耗尽，traceroute 利用它 |

关键理解：ICMP 封装在 IP 数据报里（IP 协议号 = 1），但它**不是传输层协议**——没有端口号、不承载应用数据，只是网络层的控制/诊断报文。`PacketParser` 按协议号分派：1=ICMP、6=TCP、17=UDP。

### 5. ARP：根据 IP 地址找到 MAC 地址（对应 `packet/ArpHeader`）

IP 地址是**端到端**的、MAC 地址是**逐跳**的：发数据前必须知道下一跳的 MAC。
ARP（地址解析协议）解决「已知 IP、求 MAC」：

```text
① 广播请求：谁是 192.168.1.1？  ->  目标 MAC 填 FF:FF:FF:FF:FF:FF，发往整个子网
② 单播应答：192.168.1.1 的 MAC 是 11:22:33:44:55:66  ->  只有目标主机应答
③ 存入 ARP 缓存：IP->MAC 映射缓存几分钟，之后直接单播，不必每次广播
```

ARP 报文（以太网+IPv4 固定 28 字节）字段：

| 字段 | 大小 | 典型值 | 含义 |
|------|------|--------|------|
| 硬件类型 | 2 字节 | 1 | 链路层类型（1 = 以太网） |
| 协议类型 | 2 字节 | 0x0800 | 要解析的地址类型（IPv4） |
| 硬件/协议地址长度 | 各 1 字节 | 6 / 4 | MAC 6 字节、IP 4 字节 |
| 操作码 | 2 字节 | 1=请求 2=回复 | 广播询问 / 单播应答 |
| 发送方 MAC + IP | 6 + 4 字节 | — | 请求方自己的地址 |
| 目标 MAC + IP | 6 + 4 字节 | — | 请求时目标 MAC 为 0 |

关键理解（面试常问）：
- **ARP 只工作在同一个子网内**：跨子网数据先发给网关，网关的 MAC 也是用 ARP 解析的。
- ARP 报文**不经过 IP 层**：直接封装在以太网帧里（EtherType=0x0806），所以 `PacketParser` 在解析 IP 之前先按 EtherType 分派。
- 注意两个容易混淆的字段：以太网帧头的 EtherType=0x0806（表示帧里是 ARP），而 ARP 报文内部的协议类型=0x0800（表示要解析的是 IP 地址）。
- **免费 ARP（Gratuitous ARP）**：主机主动广播自己的 IP->MAC 映射，用于 IP 冲突检测、故障切换。
- 安全：ARP 无认证，可被伪造（ARP 欺骗/中间人），现代网络常用静态 ARP 或端口安全缓解。

### 6. 三次握手与四次挥手（配合首部理解）

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

**RST（连接重置，面试常问）：** 除了 FIN 的优雅关闭，TCP 还有强制的 RST 重置。`TcpStateMachine` 已支持 `RECV_RST` 事件：

| 场景 | 事件 → 状态 | 典型报错 |
|------|------------|---------|
| 端口未监听 | SYN_SENT + RST → CLOSED | Connection refused（连接被拒绝） |
| 对端进程崩溃/异常退出 | ESTABLISHED + RST → CLOSED | Connection reset by peer |
| 半开连接（对端已消失）收到数据 | 回 RST 通知对端连接不存在 | 写入已关闭的 socket |
| 服务端 LISTEN 收到 RST | 丢弃，继续监听 | 不影响已有连接 |
| TIME_WAIT 收到 RST | 忽略，2MSL 固定等待 | 不能提前结束 |

**半开连接检测（Half-open，面试常问）：** 对端崩溃/断电/网络断开时本端收不到 FIN/RST，
连接资源（TCB、端口）被白白占用。`TcpStateMachine` 新增 `RETRANSMIT_TIMEOUT` 事件模拟建连阶段的检测：

```text
客户端发 SYN -> 等 SYN+ACK 超时 -> RTO 指数退避重发（1s -> 2s -> 4s...）
  -> 重试耗尽（默认 3 次）：客户端 SYN_SENT -> CLOSED（报 Connection timed out）
  ->                         服务端 SYN_RECEIVED -> LISTEN（放弃该连接，继续监听）
```

- 只有等待握手应答的状态（SYN_SENT/SYN_RECEIVED）允许 RTO，其余状态收到该事件抛非法转换。
- 每次重新发起握手 / 握手成功 / 放弃连接后，重试计数清零（下一次从头算）。

**keep-alive 假死检测（连接建立之后，面试常问）：** 连接空闲后对端进程崩溃/网络断开，
本端不知道——`TcpStateMachine` 新增 `PROBE_TIMEOUT` 事件模拟探测：

```text
ESTABLISHED + 空闲 -> 发探测包 -> 超时无响应 -> 再发（计数 +1）
  -> 连续 MAX_PROBES=3 次无响应 -> 判定对端假死 -> CLOSED（释放连接）
  -> 期间收到对端任何报文（RECV_*）-> 计数清零（对端还活着）
```

- 网络层没有心跳协议，TCP keep-alive 就是最朴素的"对方还在吗"（Linux 默认探测 9 次、每次间隔 75s，约 11 分钟判定假死）。
- 应用层心跳（带业务语义的 PING/PONG）见 module-11 的 `IdleStateHandler`，两者解决的是一样的"传输层连接 ≠ 业务层存活"问题。
- `PROBE_TIMEOUT` 只在 ESTABLISHED 合法，其余状态抛非法转换。

本模块的 `TcpHeader` 可构造 SYN/FIN/ACK 报文观察标志位，`TcpStateMachine` 可模拟完整状态流转（含 RST 重置与半开连接检测）；真实抓包用 `tcpdump`/Wireshark（或 `curl -v`）。

### 7. IP 地址与子网划分（对应 `ip/SubnetCalculator`）

CIDR 用「网络地址 + 前缀长度」（如 `192.168.1.0/24`）描述一个子网：前 24 位是网络位，后 8 位是主机位。

**核心计算（面试手算题）：**

| 要算什么 | 公式 | /24 例子 |
|---------|------|---------|
| 子网掩码 | 前 prefix 位为 1、其余为 0 | 255.255.255.0 |
| 网络地址 | IP & 掩码（主机位清零） | 192.168.1.0 |
| 广播地址 | 网络地址 或上 主机位全 1 | 192.168.1.255 |
| 可用主机数 | 2^(32-prefix) - 2 | 254 台 |
| 第一个可用 IP | 网络地址 + 1 | 192.168.1.1 |
| 最后一个可用 IP | 广播地址 - 1 | 192.168.1.254 |

**关键理解：**
- 网络地址与广播地址**不能**分配给主机：网络地址标识子网本身，广播地址发给子网内所有主机。
- 判断 IP 是否属于某子网 = `IP & 掩码 == 网络地址`，这正是路由器查表匹配的过程。
- 经典边界：**/30 可用 2 台**（点对点链路，如公网段）、**/31 可用 2 台**（RFC 3021，PPP 点对点，无网络/广播地址）、**/32 可用 1 台**（单主机路由，云上单机 IP 常用）。
- 等分子网：把 /24 切成 4 个 /26（2^(26-24) 个），每个跨度 64 个地址；只能切小不能合并。
- 私有地址段（面试常问）：10.0.0.0/8、172.16.0.0/12、192.168.0.0/16。

`SubnetCalculator` 可算出任意前缀的掩码/网络/广播/主机范围，`split` 可做等分子网划分，`contains` 演示路由表归属判断。

### 8. TCP 拥塞控制（对应 `protocol/TcpCongestionControl`）

**最容易混淆的两个窗口（面试第一问）：**

| 窗口 | 谁维护 | 防什么 | 单位 |
|------|--------|--------|------|
| rwnd（接收窗口） | 接收方告知 | 防止发送方淹没**接收方**（对端缓冲区） | 字节 |
| cwnd（拥塞窗口） | 发送方自己维护 | 防止发送方淹没**网络**（中间路由器队列） | MSS |

发送方实际能发的数据量 = **min(cwnd, rwnd)**（有效窗口），同时受接收方能力和网络承载能力限制。

**拥塞控制四大算法（TCP Reno，cwnd 每 RTT 更新一次）：**

```text
① 慢启动 Slow Start        cwnd 每 RTT 翻倍（1→2→4→8→16）：快速探测带宽，达到 ssthresh 转入②
② 拥塞避免 Congestion       cwnd 每 RTT +1（16→17→18）：接近瓶颈时线性试探
③ 超时（RTO 到期）         ssthresh=cwnd/2，cwnd=1：最严重拥塞信号，全盘重来
④ 快重传+快恢复（3 个重复 ACK） ssthresh=cwnd/2，cwnd=ssthresh+3：网络还能通，不归零
⑤ ECN 响应（收到 ECE，RFC 3168） cwnd/ssthresh 减半、进拥塞避免、回 CWR：拥塞信号提前到达，不必等丢包
⑥ SACK 精细化重传（重复 ACK + SACK 块） 用 SackBlock.gaps 只重传丢失段，不重发已收到的
```

**为什么要有 ③ 和 ④ 两种丢包处理（面试常问）：**
- **超时**：报文可能在网络中排队很久甚至被丢弃，网络可能已严重拥塞 → 必须把 cwnd 打回 1 重新慢启动。
- **3 个重复 ACK**：说明后续数据还能正常到达（只是某个段丢了/乱序），网络仍通 → 只把 cwnd 砍半，进入快恢复，收到新 ACK 后收敛回 ssthresh 进入拥塞避免，避免吞吐量断崖式下跌。
- 快恢复期间每多收一个重复 ACK，cwnd 临时 +1（补偿已发到网络中的数据）。

**ECN（显式拥塞通知，RFC 3168）与 SACK（RFC 2018）——对经典算法的增强：**
- **ECN**：中间路由器队列快满时直接给数据包打 **CE 标记**（而不是丢弃），接收方回 **ECE** 标志，发送方 `onEcnCe()` 把 cwnd/ssthresh 减半并回 **CWR** 确认——拥塞信号**提前到达，不必靠丢包感知**（丢包会损失吞吐，ECN 是「零丢包」的拥塞信号）。同一拥塞事件只减半一次（防抖），收到新 ACK/RTT 推进后允许再次响应。
- **SACK**：接收方用 SACK 选项回报「哪些乱序区间已收到」，发送方 `onDuplicateAckWithSack()` 用 `SackBlock.gaps` 精确算出需重传的段（块之间的空隙），只补丢失的、不重发已收到的——高带宽高延迟链路上收益巨大。

典型 cwnd 曲线：慢启动指数上升 → 拥塞避免线性上升 → 超时掉回 1 → 再次慢启动 → 3 个重复 ACK 只砍半不归零。`TcpCongestionControl` 用「状态 + 事件」完整模拟这条曲线（`onRttAcknowledged`/`onTimeout`/`onDuplicateAck`/`onNewAck`/`onEcnCe`/`onDuplicateAckWithSack`），配合 `TcpStateMachine`（连接状态）可理解 TCP 从建连、传输到断连的全过程。

### 9. 校验和：IP 反码和 与 TCP/UDP 伪首部（对应 `packet/Checksums`）

校验和算法（RFC 1071）三步：**16 bit 大端字累加 → 进位折叠回低 16 位 → 取反码**。
校验时把「数据 + 算出的校验和」整体再算一遍，结果应为 `0xFFFF`（全 1），否则报文已损坏。

**IP 首部校验和**：只覆盖 IP 首部本身（不含上层数据），计算时校验和字段先置 0。经典验证向量（RFC 1071 示例）：`45 00 00 73 ... c0 a8 00 c7` → `0xB861`。

**TCP/UDP 伪首部校验和**：覆盖「伪首部 + 报文段」，伪首部是 12 字节虚拟头，**不随报文传输**：

```text
| 源 IP (32) | 目的 IP (32) | 0 (8) | 协议号 (8) | TCP/UDP 长度 (16) |
```

**为什么要有伪首部（面试常问）**：传输层只看到端口号、感知不到 IP 地址。
若不覆盖 IP 地址，报文被路由到错误主机时接收方无法发现；协议号防止跨协议误判（TCP 报文被当 UDP 解析）。
伪首部把 IP 地址「借」进校验范围，代价是 TCP/UDP 校验和必须知道源/目的 IP 才能计算。

| 细节 | 说明 |
|------|------|
| TCP 校验和 | **必须**计算，不可省略 |
| UDP 校验和 | IPv4 下**可选**（置 0 表示未计算），IPv6 下强制 |
| 奇数长度数据 | 计算时末尾补 0x00，不参与传输 |
| 计算结果为 0 | 反码和特例：0 以 0xFFFF 传输（避免与「未计算」混淆） |

本模块 `TcpHeader.computeChecksum` / `UdpHeader.computeChecksum` 实现完整计算，`Checksums.verifyTransport` 实现整体验证。

### 10. IP 分片与 MTU（对应 `packet/IpHeader` 分片字段）

IP 报文超过链路 MTU（如以太网 1500 字节）时，路由器把报文切成多个**分片**分别转发，接收方重组。分片由首部三件套描述：

| 字段 | 位数 | 作用 |
|------|------|------|
| 标识 identification | 16 bit | 同一数据报的所有分片共享，接收方据此分组重组 |
| 标志 flags | 3 bit | bit0 保留(恒 0)；**DF**（Don't Fragment）= 0x2 禁止分片；**MF**（More Fragments）= 0x1 后面还有分片，最后一片 MF=0 |
| 片偏移 fragmentOffset | 13 bit | 本分片在原报文中的偏移，**单位 8 字节**（13 bit × 8 = 64KB，正好覆盖 IP 最大报文） |

**关键理解：**
- 片偏移单位是 8 字节不是 1 字节：13 bit 表示不了 65535 个字节偏移，×8 后正好够用（如偏移 1480 字节 → 字段值 185）。
- 每个分片都有完整的 IP 首部，只是总长度、标志、片偏移不同；分片只发生在 IP 层，TCP/UDP 感知不到。
- 现代 TCP 一般用 **Path MTU Discovery**（探测路径最小 MTU，DF=1 禁止中间分片）：
  分片重组开销大、易受攻击（分片炸弹），把大报文问题留在传输层解决，IP 层尽量不分片。

`IpHeader` 现支持分片字段的编码/解析（`FLAG_DF`/`FLAG_MF`、`withFragmentation`），`Main` 演示了 MF=1、片偏移 185 的分片报文。

### 11. TLS 握手：应用层加密通道的建立（对应 `tls/TlsHandshakeDemo`）

HTTPS 就是在 TCP 之上先做一次 TLS 握手、再加密传输。握手是客户端与服务端**协商参数 + 互相证明身份**的过程，运行 `TlsHandshakeDemo`（纯 JDK `SSLSocket`）会开启 JSSE 握手跟踪（`javax.net.debug=ssl:handshake`），打印 TLS 1.3 每一步的真实报文：

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

### 12. DNS：域名解析的报文格式（对应 `packet/DnsHeader` + `packet/DnsQuestion`）

DNS 把域名解析成 IP 地址（www.example.com -> 93.184.216.34），是浏览器上网前必发的一个 UDP 查询。

**解析过程（面试常问）：**
```text
浏览器 -> 本地 DNS（递归查询，帮你查到底）
         -> 根服务器（告诉你 .com 的地址）
         -> .com 顶级域服务器（告诉你 example.com 的地址）
         -> example.com 权威服务器（告诉你 www 的 IP）
         <- 逐级返回，本地 DNS 缓存结果
```

**报文结构**：`12 字节头部 + 问题记录 + 回答/授权/附加记录`，运行在 **UDP 53 端口**。

| 头部字段 | 大小 | 含义 |
|---------|------|------|
| 事务 ID | 16 bit | 把请求与响应配对，ID 不匹配的响应直接丢弃（防伪造） |
| 标志 | 16 bit | **QR**（0=查询 1=响应）、**Opcode**、AA、**TC**（截断）、**RD**（期望递归）、**RA**（可递归）、RCODE（0=NOERROR、3=NXDOMAIN） |
| QDCOUNT / ANCOUNT / NSCOUNT / ARCOUNT | 各 16 bit | 四种记录各有多少条，告诉解析器后面怎么切 |

问题记录里 QNAME 用**标签编码**表示域名：`[长度][标签]...`，0x00 结束，`www.example.com` → `03 77 77 77 07 65 78 61 6D 70 6C 65 03 63 6F 6D 00`；QTYPE（1=A、28=AAAA、5=CNAME、15=MX）、QCLASS（1=IN）。

关键理解：
- 为什么 DNS 用 UDP：查询响应通常一个数据报就装下，UDP 快且开销小；应答超过 512 字节（TC 置位）或需要可靠传输时切到 **TCP 53** 重查。
- 标签编码约束：单个标签 ≤ 63 字节、完整域名 ≤ 255 字节（所以域名最长就是 253 个字符）。
- 响应里的域名可**压缩**：重复出现的名字写一个 0xC0 开头的 2 字节指针指向报文前面出现过的位置（省空间）；查询记录里不会出现，本模块解析到会明确拒绝（见 `DnsQuestion` 注释）。
- 本模块 `DnsHeader` + `DnsQuestion` 可独立编解码一条完整 DNS 查询（`Main` 演示：头部 12 字节 + 查询记录 21 字节 = 33 字节）。

### 13. HTTP：应用层请求/响应报文（对应 `packet/HttpRequest` + `packet/HttpResponse`）

HTTP 是浏览器与服务器之间的文本协议，报文由「起始行 + 头部 + 空行 + 主体」组成（行结束符 CRLF）：

```text
请求:  GET /index.html HTTP/1.1\r\n        <- 请求行：方法 SP URI SP 版本
       Host: www.example.com\r\n           <- 头部：字段名: 值（名字不区分大小写）
       \r\n                                <- 空行：头部结束（必须有）
       请求体（GET 通常为空）

响应:  HTTP/1.1 200 OK\r\n               <- 状态行：版本 SP 状态码 SP 原因短语
       Content-Type: text/html\r\n
       Content-Length: 13\r\n
       \r\n
       <h1>Hello</h1>                      <- 响应体
```

**状态码语义（面试必背，按百位记）：**

| 类别 | 含义 | 典型状态码 |
|------|------|-----------|
| 1xx | 信息 | 100 Continue |
| 2xx | 成功 | 200 OK、201 Created、204 No Content |
| 3xx | 重定向 | 301 永久、302 临时、**304 未修改（走缓存，性能优化点）** |
| 4xx | 客户端错误 | 400 格式错误、401 未认证、403 禁止、404 不存在 |
| 5xx | 服务端错误 | 500 内部错误、502 网关错误、503 服务不可用 |

关键理解（面试常问）：
- **HTTP 无状态**：服务器不保存客户端状态，靠 Cookie/Session 维持会话（见 module-03）。
- **多值头**：一个字段名可以出现多次（多个 `Cookie`/`Set-Cookie`/`Via`），解析时值按顺序追加而不是覆盖，`HttpRequest` 和 `HttpResponse` 都用 `Map<String, List<String>>` 保存（`header()` 取第一个值，`headerValues()` 取全部）。
- **Content-Length 与粘包**：HTTP/1.1 默认 Keep-Alive 复用连接，响应长度靠 Content-Length（或 chunked）切分，否则无法判断响应何时结束——这就是粘包问题在 HTTP 里的表现（对应本模块粘包章节）。
- 状态码 3 位数字 + 原因短语配套：程序看状态码、人看原因短语；原因短语可以含空格（如 Not Found）。
- HTTP/1.1 vs 1.0：1.1 默认长连接、支持 Host 头（一台服务器多个域名）、支持 chunked/断点续传。
- 本模块只做协议解析；真实 HTTP 服务（Netty 版 `/hello`、`/health` 路由）见 module-11。

**纯 JDK 最小 HTTP 客户端/服务器（`socket/MinimalHttpClient` + `socket/MinimalHttpServer`）** 把上面的协议类真实跑起来：

- 客户端 `request()`：Socket 连接 → `HttpRequest.encode()` 发请求 → **逐字节读响应头到 CRLFCRLF**（不用 BufferedReader，避免缓冲吞掉响应体字节）→ 从头部取 Content-Length → `readExactly` 循环收齐响应体（一次 read 可能只到半个包，必须循环）。
- `Transfer-Encoding: chunked` → **按块循环读**：每块是 `[十六进制大小][;扩展]\r\n[数据]\r\n`，直到 `0` 块（RFC 7230 4.1），0 块后的 trailer 头部跳过；每块**自己声明长度**，服务端可流式边生成边发送，不必预先知道总长度。
- 没有 Content-Length 也没有 Transfer-Encoding（HTTP/1.0 或 `Connection: close`）→ 读到 EOF 兜底。
- 服务端 `readRequest()`：与客户端镜像——逐字节读请求头到空行，按 Content-Length 精确读请求体，再 `HttpResponse.text()` 回响应（自动算 Content-Type/Content-Length）。
- 这正是**粘包问题的 HTTP 版落地**：请求/响应都是「头部 + 空行 + 主体」，主体长度靠 Content-Length 切分，两条报文不会混在一起（对照 socket 章节的长度头帧协议）。
- **请求走私防护**：同时声明 Content-Length 与 Transfer-Encoding 是攻击特征（RFC 7230 3.3.3），客户端直接拒绝。
- **gzip 解压**：响应头 `Content-Encoding: gzip` 时响应体是压缩字节（Content-Length 是压缩后长度），客户端读完原始字节后用 `GZIPInputStream` 解压成明文（`HttpConnection` 自动处理）。
- **304 条件缓存**：服务器给资源标 `ETag`；客户端带 `If-None-Match` 请求，命中则回 **304 Not Modified**（无响应体），浏览器直接用本地缓存——省去重新传输响应体，是最常见的性能优化点（`MinimalHttpServer` 的 `/etag` 路由演示）。
- **管线化（Pipelining）**：`HttpConnection.pipeline()` 一次写出多个请求（一个系统调用），再按序读回——响应顺序与请求顺序一致，每个响应必须有明确边界。
- **重定向跟随**：`requestFollowingRedirects()` 自动跟随 3xx + `Location`（支持相对路径与绝对 URL，同主机），超过 maxHops 放弃；跨主机需要新建连接，留作练习。

**Keep-Alive 连接复用（`socket/HttpConnection`）——粘包问题的终极考验：**

HTTP/1.1 默认长连接：一次三次握手承载多个请求，省去重复握手成本。复用的前提是**每条响应边界清晰**——这就是为什么 Content-Length/chunked 如此重要：读不完一条响应，下一条的字节就会被当成本条主体（粘包）。

```text
客户端 HttpConnection:             服务端 MinimalHttpServer:
request(/)    ────────────────────>  读请求 -> 回响应（Content-Length）
request(/hello) ──────────────────>  同一连接继续读下一个请求（Keep-Alive 循环）
request(/chunked) ────────────────>  回 chunked 响应（块边界清晰，仍可复用）
request(/nope) ───────────────────>  回 404
close()       <────────────────────  读到 EOF，结束这条连接
```

- 客户端 `HttpConnection.isReusable()` 判定连接是否还能复用：**Connection: close**、**无长度读到 EOF**（对端已关）、**HTTP/1.0 默认关闭**（除非显式 keep-alive）——三种情况读完响应后连接自动关闭。
- 服务端 `MinimalHttpServer` 的 `handle` 改为 Keep-Alive 循环：一条连接上连续读请求、回响应，直到请求头带 `Connection: close` 才结束（此时响应也带 `Connection: close` 头告知客户端）。
- `MinimalHttpClient` 是单请求便捷入口（请求头带 `Connection: close`，读完即关）；要复用连接就用 `HttpConnection`。
- 留作练习：重定向跟随、Cookie 会话、HTTPS、连接池/并发复用。

## 🌐 协议使用场景（选型速查）

> 从「要解决什么问题」出发：可靠性/延迟/寻址/诊断各选什么协议。与 module-18 的密码学选型速查（[docs/crypto-cheatsheet.md](../docs/crypto-cheatsheet.md)）同理，这里是网络协议版。

| 协议/技术 | 适用场景 | 典型应用 |
|---|---|---|
| 以太网帧 | 局域网内逐跳传输数据单元（MAC 寻址） | 交换机二层转发、局域网通信 |
| IPv4 | 端到端寻址与路由（网络层） | 公网/内网互联、路由器转发 |
| IP 分片/MTU | 报文跨不同 MTU 链路传输 | 大包跨异构链路；现代用 Path MTU Discovery 避免分片 |
| TCP | 可靠字节流：有序/去重/重传/流量/拥塞控制 | HTTP/HTTPS、FTP、SMTP、数据库连接 |
| TCP 选项 | 性能协商：MSS/Window Scale/SACK/时间戳 | 高带宽高延迟链路（长肥网络）、吞吐优化 |
| TCP 状态机 | 连接建立/释放/异常处理 | 服务端连接管理、连接池、半开/假死检测 |
| TCP 拥塞控制 | 避免网络拥塞崩溃，公平共享带宽 | 广域网传输、数据中心、CDN 回源 |
| UDP | 无连接低延迟数据报，可容忍丢包 | DNS、RTP 音视频、DHCP、游戏、NTP |
| ICMP | 网络层诊断与控制 | ping、traceroute、错误报告 |
| ARP | 局域网内 IP→MAC 解析 | 二层转发、免费 ARP（IP 冲突检测/故障切换） |
| DNS | 域名→IP 解析 | 浏览器上网、CDN 调度、邮件路由（MX） |
| HTTP | Web 请求/响应（状态码、多值头、Keep-Alive、chunked） | 浏览器-服务器、REST API |
| TLS | 传输加密 + 身份认证 | HTTPS、邮件加密、API 安全 |
| TCP/UDP Socket | 进程间网络通信编程 | 客户端-服务器应用、回显/网关 |
| 长度头帧协议 | 给 TCP 字节流划帧（解决粘包/拆包） | 自定义 TCP 协议、Netty LengthFieldBasedFrameDecoder |
| CIDR 子网 | 网络规划与路由聚合 | 企业网段规划、云 VPC、路由表匹配 |

**TCP vs UDP 一分钟决策：**

| 需求 | 选 |
|---|---|
| 文件/网页/数据库（要可靠有序） | TCP |
| 音视频/游戏/域名（要低延迟、可丢包） | UDP |
| 自定义 TCP 协议 | 加长度头帧协议（粘包解决） |
| 大包跨异构链路 | 分片，或 Path MTU Discovery 不分片 |
| 要加密传输 + 认证身份 | TCP/TLS（HTTPS） |

## 🧪 测试

| 测试类 | 验证内容 | 不验证的内容 |
|--------|----------|--------------|
| `TcpHeaderTest`（14） | 编码/解析往返、**9 个标志位（含 URG/CWR/ECE/NS）字节位置**、数据偏移决定首部长度、**带选项首部（MSS/多选项/越界拒绝/选项参与校验和）** | 真实网络行为 |
| `TcpOptionTest`（18） | MSS/WS/SACK/时间戳 构造、NOP 对齐编码、EOL 终止、多选项组合、非法长度拒绝、未知 Kind 透传、**SACK 块（单块/多块/字节布局/序号回绕/MSS 组合上限/非法构造与报文拒绝）** | 真实 TCP 协商 |
| `SackBlockTest`（10） | 区间语义（右开/回绕）、**丢包区间推断 gaps**（中间洞/多洞乱序/全丢/全覆盖/重叠合并/范围外忽略/回绕范围/空范围与非法参数） | 真实网络时序 |
| `UdpHeaderTest`（3） | 8 字节固定、大端字节序、负载长度计算 | 丢包/乱序 |
| `IpHeaderTest`（11） | 版本+IHL 位字段、点分十进制互转、IP 每段 0~255 校验、分片三件套编解码与非法参数、**分片重组（3 片乱序拼接/标识不一致/偏移不连续拒绝）** | 真实路由/分片 |
| `ChecksumTest`（11） | RFC 1071 IP 官方向量、反码和折叠、奇数长度补 0、TCP/UDP 伪首部校验和向量与整体验证、伪首部/数据参与校验 | 真实抓包校验 |
| `EthernetFrameTest`（4） | 14 字节帧头、MAC 地址转换、EtherType | 真实网卡 |
| `IcmpHeaderTest`（9） | ping 请求/回复往返、字段位置、类型名称、偏移解析、**校验和（首部+数据往返/篡改失败/奇数长度）** | 真实 ping 抓包 |
| `PacketParserTest`（7） | 完整报文 TCP/UDP/**ICMP** 分层解析、**按 EtherType 分派 ARP**、未知协议/EtherType 拒绝、**构造 IP+UDP+DNS 完整查询报文（53 端口/域名解析）** | 真实抓包 |
| `ArpHeaderTest`（7） | 28 字节固定、请求/回复操作码、字段位置、偏移解析、非法参数、**免费 ARP（发送方=目标=自己/广播 MAC）** | 真实 ARP 广播 |
| `DnsHeaderTest`（9） | 12 字节固定、查询/响应工厂、标志位字节布局、TC/RA 组合、NXDOMAIN、偏移解析、非法参数、**withId 拷贝（换事务 ID 其余不变）** | 真实 DNS 服务器 |
| `DnsQuestionTest`（9） | 标签编码（[3]www[7]example[3]com[0]）、往返、QTYPE 描述、紧跟头部解析、压缩指针拒绝、非法域名、**多问题报文（QDCOUNT=2 连续解析）** | 真实域名解析 |
| `HttpRequestTest`（10） | 请求行/头部/请求体往返、POST+Content-Length、**多值头（多个 Cookie 往返/重复头追加/大小写不敏感读取）**、LF 容错、顺序保持、非法报文拒绝 | 真实浏览器/服务器 |
| `HttpResponseTest`（11） | 状态行（含空格原因短语）往返、状态码语义/类别、自动补原因短语、2xx 判断、非法状态行拒绝、body 含换行、**多值头（多个 Set-Cookie 各占一行/顺序追加/header 取第一个）**、**notModified 工厂（ETag/304）**、withHeader 不可变拷贝 | 真实浏览器/服务器 |
| `TransportProtocolTest`（4） | TCP/UDP 属性与首部长度对比、协议号反查 | — |
| `TcpStateMachineTest`（31） | 三次握手、主动/被动四次挥手、TIME_WAIT 归属、同时关闭、**RST 连接重置（拒绝/重置/忽略）**、**半开连接检测（SYN 重传超时/计数重置/非法状态）**、**keep-alive 假死检测（IDLE_TIMEOUT 进入探测/探测超时/对端响应退出探测/非法状态）**、非法转换拒绝 | 真实网络时序 |
| `SubnetCalculatorTest`（18） | 掩码转换、网络/广播地址、主机范围、可用主机数（含 /30、/31、/32 边界）、归属判断、等分子网、**私网判断（RFC 1918 三段/边界之外）**、**10.0.0.0/8 等分 256 个 /16** | 真实路由表 |
| `TcpCongestionControlTest`（23） | 慢启动指数增长、拥塞避免线性增长、超时重置、快重传/快恢复、有效窗口 min(cwnd, rwnd)、参数校验、**SACK 精细化重传（只重传块空隙/全覆盖无重传）**、**ssthresh 手动设置**、**超时后阈值翻倍加速恢复**、**ECN 响应（ECE 减半/不归零/同事件防抖/新 ACK 与 RTT 重置防抖）** | 真实网络拥塞 |
| `SocketIntegrationTest`（4） | **真实回环** TCP/UDP 回显、粘包 vs 有边界 | 跨主机网络 |
| `MinimalHttpClientTest`（9） | **真实回环** GET 往返（按 Content-Length 收 body）、**拆包分 5 片收齐**、无 Content-Length 读到 EOF、**chunked（单块/多块十六进制大小/分片写拆包/trailer 跳过/CL+TE 走私拒绝）**、**MinimalHttpServer 配套 200/404/405/chunked** | 跨主机网络、公网站点 |
| `HttpConnectionTest`（10） | **真实回环 Keep-Alive 复用**：连发 3 请求切分正确、Connection: close 后不可复用、HTTP/1.0 无长度 EOF 不可复用、chunked 后仍可继续发、MinimalHttpServer 配套 4 请求同一连接、**gzip 解压**、**管线化 3 请求一次写出**、**重定向跟随/超限/跨主机拒绝** | 跨主机网络、公网站点 |
| `FrameCodecTest`（10） | **版本+长度头编码**、粘包多帧、拆包等待、帧头分批、非法超长拒绝、**版本不匹配直接拒绝** | 真实网络 |
| `FramedTcpServerIntegrationTest`（4） | **真实回环**多帧回声、特殊字符、双客户端并发、跨 TCP 分段拼帧 | 跨主机网络 |
| `TlsHandshakeDemoTest`（1） | **真实回环** SSLSocket 握手成功、协商协议/密码套件、收到回显 | 正式证书链、主机名校验 |

> 共 246 个测试，全部带 `@DisplayName`。Socket 测试用随机端口，不依赖固定端口。

## 🧪 测试用例与守护场景（逐用例）

> 246 个测试按类分组，每一行回答「这些用例在守护什么场景」；与上面「验证内容」汇总表互补（那里讲测什么，这里讲为什么重要）。

### 报文解析（packet）

**EthernetFrameTest（4）— 二层帧头**

| 用例（组） | 守护场景 |
|---|---|
| 帧头往返、EtherType=0x0806、MAC 大小写不敏感/长度 6、不足 14 字节拒绝 | 以太网帧头编解码正确 + 防御畸形帧 |

**IpHeaderTest（11）— IP 首部与分片**

| 用例（组） | 守护场景 |
|---|---|
| 首部往返、版本+IHL 位字段、点分十进制互转、带偏移解析 | IPv4 首部编解码正确 |
| 分片三件套（DF/MF/片偏移 185、最大 8191）、标志字节布局 | 分片语义：MF=1 后还有分片、偏移单位 8 字节 |
| 分片重组（3 片乱序拼接；标识不一致/偏移不连续拒绝） | 接收方重组正确 + 防分片炸弹式攻击 |
| 非法分片参数、不足 20 字节拒绝 | 防御畸形输入 |

**TcpHeaderTest（14）— TCP 首部**

| 用例（组） | 守护场景 |
|---|---|
| SYN/ACK+FIN 往返、数据偏移位字段 | 首部编解码与「数据偏移决定长度」 |
| 9 个标志位（URG/CWR/ECE/NS）字节位置、全置位往返 | 每个标志的准确 bit 位（抓包对照基础） |
| 带选项首部（MSS 自动 dataOffset=6、多选项、越界拒绝、选项参与校验和） | 选项区域编解码与校验和覆盖 |
| 不足 20 字节、带偏移解析 | 防御畸形报文 |

**TcpOptionTest（18）— TCP 选项**

| 用例（组） | 守护场景 |
|---|---|
| MSS/WS/SACK-Permitted/Timestamp 构造往返、NOP 对齐、EOL 终止、多选项组合 | 选项二进制编码正确（面试手算基础） |
| SACK 块（单/多块、字节布局、序号回绕、MSS 组合上限 40 字节） | 乱序确认区间语义 |
| 非法构造/报文拒绝、未知 Kind（TFO 0x1E）透传 | 防御畸形输入 + 向前兼容 |

**SackBlockTest（10）— SACK 丢包推断**

| 用例（组） | 守护场景 |
|---|---|
| 右开区间 [left, right)、跨 2^32 回绕 | 区间长度/包含判断正确 |
| gaps 丢包推断（中间洞/多洞乱序/全丢/全覆盖/重叠合并/范围外忽略/回绕/空范围非法） | 拥塞控制「只重传丢失段」的核心算法（块空隙 = 需重传段） |

**UdpHeaderTest（3）— UDP 首部**

| 用例（组） | 守护场景 |
|---|---|
| 固定 8 字节、大端字节序、不足 8 字节拒绝 | UDP 首部编解码与防御 |

**ChecksumTest（11）— 校验和完整性**

| 用例（组） | 守护场景 |
|---|---|
| RFC 1071 官方向量（0xB861）、反码折叠、奇数长度补 0 | 算法与标准一致（防实现回归） |
| TCP/UDP 校验和向量 + 覆盖数据/伪首部 + 整体验证 0xFFFF + 篡改一字节失败 | 传输层完整性：换 IP/改数据都能被发现 |

**IcmpHeaderTest（9）— ICMP（ping）**

| 用例（组） | 守护场景 |
|---|---|
| ping 请求/回复往返、字段位置、类型名称、带偏移解析 | ICMP 报文编解码（真实 ping 对照） |
| 校验和往返/篡改失败/奇数长度 | ICMP 完整性校验 |

**ArpHeaderTest（7）— ARP**

| 用例（组） | 守护场景 |
|---|---|
| 请求/回复往返、字段位置、带偏移解析 | ARP 报文编解码 |
| 免费 ARP（发送方=目标=自己、广播 MAC、opcode=1） | IP 冲突检测/故障切换场景 |
| 不足 28 字节、MAC 长度非法拒绝 | 防御畸形报文 |

**DnsHeaderTest（9）— DNS 头部**

| 用例（组） | 守护场景 |
|---|---|
| 查询/响应往返、标志位字节布局、NXDOMAIN、TC 截断、带偏移解析 | DNS 头部语义正确 |
| withId 拷贝（换事务 ID 其余不变） | 事务 ID 不匹配的响应被丢弃（防伪造） |
| 非法参数、不足 12 字节拒绝 | 防御畸形报文 |

**DnsQuestionTest（9）— DNS 域名编码**

| 用例（组） | 守护场景 |
|---|---|
| 标签编码/往返（任意多层域名）、QTYPE 描述（A/AAAA/CNAME/MX）、多问题 QDCOUNT=2 | 域名压缩编码正确（字节级） |
| 压缩指针（0xC0）明确拒绝 | 查询记录里不允许指针（安全边界） |
| 非法域名、标签未以 0x00 结束拒绝 | 防御畸形输入 |

**PacketParserTest（7）— 分层解析**

| 用例（组） | 守护场景 |
|---|---|
| 以太网+IP+TCP/UDP/ICMP 逐层解析、EtherType 分派 ARP、完整 IP+UDP+DNS 查询报文（53 端口） | 模拟 Wireshark 逐层剥离的分派正确性 |
| 未知协议号/EtherType 拒绝 | 防御未知协议 |

**HttpRequestTest（10）— HTTP 请求**

| 用例（组） | 守护场景 |
|---|---|
| GET/POST 往返、Content-Length 与请求体校验、LF 容错、头部顺序保持 | 请求报文解析正确（真实网络 CRLF） |
| 多值头（多个 Cookie 各占一行按序追加、header 取第一个、大小写不敏感） | 真实浏览器多 Cookie 场景 |
| 非法请求行/头部行拒绝 | 防御畸形请求 |

**HttpResponseTest（10）— HTTP 响应**

| 用例（组） | 守护场景 |
|---|---|
| 状态行往返（含空格原因短语）、状态码类别/标准短语、2xx 判断 | 状态码语义（程序看码、人看短语） |
| 多值头 Set-Cookie 按序追加、body 含 CRLF 完整解析 | 响应边界与多值头场景 |
| 304 notModified 工厂（ETag）、withHeader 不可变拷贝 | 条件缓存（304 省流量）与不可变语义 |
| 非法状态行拒绝 | 防御畸形响应 |

### 协议与状态（protocol）

**TransportProtocolTest（4）— TCP vs UDP**

| 用例（组） | 守护场景 |
|---|---|
| TCP/UDP 属性对比、首部开销 20 vs 8、协议号反查（6/17） | 面试对比表数据正确（防记错） |

**TcpStateMachineTest（31）— 连接状态机**

| 用例（组） | 守护场景 |
|---|---|
| 三次握手/四次挥手（主动/被动/同时关闭）、TIME_WAIT 归属、11 个状态完整集合 | RFC 793 状态流转正确（面试核心） |
| RST 重置（端口未监听/对端强杀/挥手中途/LISTEN 丢弃/TIME_WAIT 忽略） | 异常连接处理：拒绝/重置/忽略的区分 |
| 半开连接（SYN 重传 2 次等待、第 3 次 RTO 放弃、计数重置、非法状态拒绝） | 建连阶段对端消失检测（Connection timed out） |
| keep-alive 假死（IDLE_TIMEOUT 进入探测、3 次判定假死、收到报文退出、非法状态拒绝） | 连接保活与资源释放（对端崩溃/断网检测） |
| 非法转换拒绝（ESTABLISHED 收 SYN、CLOSED 收 FIN、TIME_WAIT 收 ACK） | 状态机健壮性 |

**TcpCongestionControlTest（23）— 拥塞控制**

| 用例（组） | 守护场景 |
|---|---|
| 初始状态、慢启动指数/拥塞避免线性、ssthresh 转换 | 四大算法基本行为 |
| 超时（减半归 1、ssthresh 下限 2）、快重传/快恢复（cwnd+3、+1、退出收敛）、有效窗口 min(cwnd, rwnd) | Reno 完整行为与两个窗口的区别 |
| ECN（ECE 减半不归零、同事件防抖、新 ACK/RTT 重置） | RFC 3168：拥塞信号提前到达、不靠丢包 |
| SACK 接入（3 重复 ACK+块只重传空隙、全覆盖无重传） | 高带宽高延迟链路的精细化重传 |
| ssthresh 手动设置、超时后阈值翻倍（配合慢启动） | 恢复加速策略 |
| 非法构造参数拒绝 | 防御错误配置 |

### 子网划分（ip）

**SubnetCalculatorTest（18）— 网络规划**

| 用例（组） | 守护场景 |
|---|---|
| 掩码互转（/8 /16 /23 /30 /32 /0）、网络/广播地址、主机位清零、可用主机数（/24=254、/30=2、/31、/32 边界） | 子网手算正确（面试题基础） |
| contains 归属判断（含网络/广播地址本身） | 路由表匹配语义 |
| 等分子网（/24→4×/26、/26→2×/27、/8→256×/16、非对齐 IP 规整、目标前缀更小拒绝） | VLSM 规划与「只能切小不能合并」约束 |
| 私网/公网判断（RFC 1918 三段、172.32、100.64 CGNAT） | 防火墙/NAT/ACL 策略边界 |
| SubnetInfo 汇总 | 一次给出全部关键地址（掩码/广播/范围/可用数） |

### Socket 与帧协议（socket）

**SocketIntegrationTest（4）— 真实回环**

| 用例（组） | 守护场景 |
|---|---|
| TCP/UDP 回声、TCP 无边界（粘包/拆包）vs UDP 正好 3 次 | 真实 socket 链路验证「TCP 字节流 vs UDP 数据报」 |

**FrameCodecTest（10）— 长度头帧协议**

| 用例（组） | 守护场景 |
|---|---|
| 编码（版本+4 字节长度头、中文按字节数） | 帧格式正确 |
| 解码（单帧/粘包多帧/拆包等待/帧头被拆/混合分批/连续累积无泄漏） | 粘包问题解决的核心：任意到达顺序都能正确拆帧 |
| 非法超长帧、版本不匹配拒绝 | 协议健壮性（不兼容直接断开） |

**FramedTcpServerIntegrationTest（4）— 帧服务器端到端**

| 用例（组） | 守护场景 |
|---|---|
| 多帧回声、特殊字符、双客户端并发互不混淆、跨 TCP 分段拼帧 | 真实回环帧协议端到端（并发与半包） |

**MinimalHttpClientTest（9）— 最小 HTTP 客户端**

| 用例（组） | 守护场景 |
|---|---|
| GET 按 Content-Length 收 body、拆包分 5 片收齐、无长度读到 EOF | 响应边界切分（HTTP 版粘包解决） |
| chunked（单块/多块十六进制大小/分片拆包/trailer 跳过）、CL+TE 走私拒绝 | chunked 解析与请求走私安全 |
| MinimalHttpServer 配套 200/404/405/chunked | 客户端-服务器端到端 |

**HttpConnectionTest（10）— Keep-Alive 连接复用**

| 用例（组） | 守护场景 |
|---|---|
| 连发 3 请求切分正确、Connection: close 不可复用、HTTP/1.0 无长度 EOF 不可复用、chunked 后仍可继续 | 连接复用判定（哪些情况必须关连接） |
| MinimalHttpServer 配套 4 请求同一连接、gzip 解压、管线化 3 请求一次写出、重定向跟随/超限/跨主机拒绝 | 真实 HTTP 客户端完整能力 |

### TLS（tls）

**TlsHandshakeDemoTest（1）— TLS 握手**

| 用例（组） | 守护场景 |
|---|---|
| SSLSocket 真实握手成功、协商协议/密码套件、收到回显 | 加密通道建链端到端（真实回环） |

## 🧯 常见问题排查

1. **端口被占用**：关闭旧进程或换端口（测试用随机端口避免冲突）。
2. **TCP 读到半条消息**：这是字节流正常现象，加帧解码器（见 module-11）。
3. **UDP 收不到回显**：UDP 无连接，先确认服务端已启动、地址端口一致；数据报可能被防火墙丢弃。
4. **IP 校验失败**：确认每段在 0~255，段数必须为 4。
5. **TCP 粘包演示不稳定**：回环网络下 read 次数可能为 1 或 2，测试断言的是"次数 < 3 且数据完整"，两者都符合。

## ✍️ 动手练习

> ✅ 已完成（收尾记录）：
> - **ECN 接入拥塞控制**（原 #1）：`TcpCongestionControl.onEcnCe()`——收到 ECE 时 cwnd/ssthresh 减半、进拥塞避免、回 CWR（同事件防抖，新 ACK/RTT 重置），测试见 `TcpCongestionControlTest`、演示见 Main 2.4b。
> - **SACK 接入拥塞控制**（原 #19）：`TcpCongestionControl.onDuplicateAckWithSack()`——3 个重复 ACK + SACK 块时用 `SackBlock.gaps` 只重传丢失段，测试见 `TcpCongestionControlTest`、演示见 Main 2.3。

1. 给 `TcpCongestionControl` 增加 **ECN 与 CWR 状态机联动**：把 `onEcnCe()` 的返回值接入 `TcpStateMachine`（收到 ECE 事件 → 减半并回 CWR 报文），形成「路由器 CE → 收方 ECE → 发方 CWR」完整闭环，补测试。
2. 用 `PacketParser` 构造一个"IP + TCP + DNS 查询"报文（DNS 走 TCP 53 的场景：响应超过 512 字节时），断言解析出 `destinationPort=53` 与 TCP 标志位。
3. 给 `FrameCodec` 增加**应用层协议类型字节**：帧头 `[版本][类型][长度][内容]`，类型区分文本/二进制/心跳（对照 module-11 群聊的心跳帧），补测试。
4. 用 `tcpdump -i lo port 19001` 抓包观察三次握手，对照 `TcpHeader` 的标志位。
5. 给 `TcpStateMachine` 增加探测**间隔**参数：`IDLE_TIMEOUT` 后按固定间隔（如 75s，Linux 默认）周期性触发 `PROBE_TIMEOUT`，而不是手动连发（提示：记录上次探测时间，未到间隔时忽略）。
6. 用 `tcpdump` 抓一个真实 ping 包，把 ICMP 报文字节拷进测试，用 `IcmpHeader.verify` 验证校验和为 0xFFFF（对照本模块 Checksums 的 RFC 1071 实现）。
7. 给 `SubnetCalculator` 增加**子网合并（超网 supernetting）**：判断多个连续子网能否合并成一个大子网（如 4 个 /26 合并为 /24），并返回合并后的 CIDR，补测试（路由聚合是面试常考）。
8. 给 `TcpCongestionControl` 增加**初始慢启动窗口**（RFC 5681 的 IW=10）：构造参数支持 `initialCwnd=10`，并验证慢启动从 10 开始翻倍。
9. 给 `SubnetCalculator` 增加 **VLSM 变长子网划分**：把 10.0.0.0/8 按需求切成不同大小的子网（如 128 个 /24 + 64 个 /25），模拟真实网络规划，补测试。
10. 给 `TcpCongestionControl` 增加 **CUBIC 拥塞控制**（Linux 默认算法）：超时后 cwnd 按三次函数 `(t-K)³` 快速回升，对比 Reno 的线性恢复（提示：新增 `Phase` 或独立类，保留 Reno 实现）。
11. 用 `tcpdump`/Wireshark 抓一个真实 TCP 包，把 IP 首部和 TCP 段的字节拷进测试，用 `Checksums` 验证校验和是否为 0xFFFF（理解校验和的实际用途）。
12. 给 `IpHeader` 增加**分片工具**：给定数据长度与 MTU，自动生成各分片（正确的标识/偏移/MF），并用 `reassembledDataLength` 验证能重组回原长度（发送端分片 + 接收端重组闭环）。
13. 给 `ArpHeader` 增加**ARP 缓存**模拟：维护 IP->MAC 映射表，收到 ARP 请求/回复时更新缓存、查询命中直接回，补测试（对照免费 ARP 的缓存更新行为）。
14. 给 `DnsHeader` 增加**响应校验**：给定查询的 id，收到响应时先比对 id 再解析（不匹配直接丢弃，模拟 DNS 防伪造），补测试。
15. 给 `DnsQuestion` 增加 **AAAA/其他类型记录解析**：目前只有查询记录（Question），增加 Answer 记录（含 TTL、RDLENGTH、RDATA）的编解码，补测试。
16. 给 `MinimalHttpServer` 增加**动态 ETag**：按响应体内容算哈希作为 ETag（内容变 ETag 变），配合 `If-None-Match` 做真正的内容缓存判据（当前是固定 `"v1"`）。
17. 给 `MinimalHttpClient` 增加 **HTTPS 支持**：把底层 `Socket` 换成 `SSLSocketFactory` 创建的 TLS 套接字（对照 `tls/TlsHandshakeDemo`），验证 `https://` 连接。
18. 给 `HttpConnection` 增加**连接池**：维护一组 `HttpConnection` 供并发请求复用（如 `ConcurrentLinkedQueue` 空闲连接），用完归还、坏连接丢弃重连（提示：对照 module-12 线程池思路）。
19. 给 `HttpConnection` 增加**跨主机重定向**：`Location` 指向不同 host 时新建连接继续跟随（提示：解析绝对 URL 的 host/port，用 `HttpConnection.connect` 新连接发起）。
20. 给 `TcpCongestionControl` 增加**拥塞窗口审计**：记录 cwnd/ssthresh 每次变化的轨迹（时间序列），供画图对比 Reno 与 CUBIC 的恢复曲线（提示：加 `List<Snapshot>` 日志，演示时导出）。

## 📄 关联模块

- [module-11-netty](../module-11-netty)：Netty 对 TCP 粘包/拆包的工业级解决（解码器、心跳、群聊）；TLS 部分有 Netty 版握手演示 `ssl/SslHandshakeDemo`，与本模块的纯 JDK 版 `tls/TlsHandshakeDemo` 相互对照。
- [module-12-multithreading](../module-12-multithreading)：Socket 服务器多线程处理的并发基础。
