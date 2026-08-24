# 网络协议选型速查表（Network Cheatsheet）

> module-15-network 配套的选型速查文档：从「要解决什么问题」出发给协议选型与对照。
> 完整演示代码见 `module-15-network`（报文解析 / Socket / 拥塞控制等），协议细节见该模块 README。
> 配套文档：[密码学选型速查表](crypto-cheatsheet.md)（module-18）。

## 0. 三问选型法

1. **要解决什么问题？** 可靠传输 / 低延迟 / 端到端寻址 / 局域网寻址 / 诊断 / 加密。
2. **数据特征？** 大文件（要可靠有序）还是音视频流（可丢包要低延迟）？单条消息多大？
3. **环境约束？** 同一子网还是跨公网？链路 MTU 多大？对端是否会消失（半开/假死）？

## 1. TCP vs UDP（重点对照）

| 维度 | TCP | UDP |
|---|---|---|
| 连接方式 | 面向连接：三次握手、四次挥手 | 无连接：发完即走 |
| 可靠性 | 可靠：确认/重传/排序/去重/流量/拥塞控制 | 不可靠：丢包不重传 |
| 有序性 | 有序交付 | 可能乱序 |
| 消息边界 | **字节流**：无边界（粘包/拆包问题） | **数据报**：一次 send 对应一次 receive |
| 首部开销 | 最少 20 字节 + 选项 | 固定 8 字节 |
| 典型应用 | HTTP/HTTPS、FTP、SMTP、数据库 | DNS、RTP 音视频、DHCP、游戏、NTP |

**决策规则：**
- 要**可靠有序**（文件/网页/数据库/消息队列）→ **TCP**。
- 要**低延迟、可容忍少量丢包**（音视频/游戏/域名查询）→ **UDP**。
- 用 **TCP 做自定义协议** → 必须自己划帧（长度头/定长/分隔符），否则粘包。
- UDP 想要可靠性 → 应用层自己实现（QUIC 的思路：UDP + 重传 + 拥塞控制）。

## 2. 分层模型速查（封装/解封装）

```
发送方:  应用数据 -> [TCP/UDP 首部] -> [IP 首部] -> [以太网帧头] -> 网线
接收方:  网线 -> 去以太网帧头 -> 去 IP 首部 -> 去 TCP/UDP 首部 -> 应用数据
```

| 层 | 典型协议 | 首部/格式 | 寻址单位 |
|---|---|---|---|
| 应用层 | HTTP、DNS、FTP、TLS | 业务数据 | 域名/URL |
| 传输层 | TCP（20B+）、UDP（8B） | 端口号 | 进程（端口） |
| 网络层 | IP（20B+）、ICMP | IP 地址 | **端到端不变** |
| 数据链路层 | 以太网（14B 帧头）、ARP | MAC 地址 | **每跳都变** |

**关键区分：** MAC 逐跳变、IP 端到端不变；ARP 解决「已知 IP 求 MAC」；ICMP 是网络层诊断（无端口、不承载应用数据）。

## 3. TCP 选项与性能（什么时候用哪个）

| 选项 | 作用 | 何时重要 |
|---|---|---|
| MSS（kind=2） | 协商最大报文段（SYN 必带，双方取小） | 几乎所有 TCP 连接 |
| Window Scale（kind=3） | 窗口左移放大（16 bit → 最大 1GB） | 高带宽高延迟链路（BDP > 64KB） |
| SACK-Permitted（kind=4） | 声明支持选择性确认 | 丢包频繁的广域网 |
| SACK（kind=5） | 回报乱序已收区间（每块 8 字节） | 配合快重传**只重传丢失段** |
| Timestamp（kind=8） | 算 RTT、防序号回绕（PAWS） | 高吞吐长连接 |

**决策规则：** 大带宽×大延迟（长肥网络）→ 必须 Window Scale + SACK；否则 64KB 窗口就是瓶颈。

## 4. 连接状态机速查

```
三次握手:  客户端 CLOSED -> SYN_SENT -> ESTABLISHED
          服务端 CLOSED -> LISTEN -> SYN_RECEIVED -> ESTABLISHED

四次挥手:  主动方 ESTABLISHED -> FIN_WAIT_1 -> FIN_WAIT_2 -> TIME_WAIT -> CLOSED
          被动方 ESTABLISHED -> CLOSE_WAIT -> LAST_ACK -> CLOSED

同时关闭:  FIN_WAIT_1 -> CLOSING -> TIME_WAIT -> CLOSED
```

| 异常场景 | 机制 | 表现 |
|---|---|---|
| 端口未监听 | RST（SYN_SENT 收到 RST → CLOSED） | Connection refused |
| 对端崩溃/强杀 | RST（ESTABLISHED 收到 RST → CLOSED） | Connection reset by peer |
| 半开连接（对端消失，建连阶段） | SYN 重传超时（RTO 指数退避，耗尽放弃） | Connection timed out |
| 假死连接（建连后对端消失） | keep-alive 探测（连续 N 次无响应判定假死） | 释放连接资源 |
| TIME_WAIT | 主动关闭方等 2MSL，RST 不能提前结束 | 确保最后一个 ACK 到达 |

**决策规则：** 服务端要处理半开/假死 → 设 TCP keep-alive 或应用层心跳（业务 PING/PONG）；TIME_WAIT 只属于主动关闭方。

## 5. 拥塞控制速查（TCP Reno + 增强）

| 算法 | 行为 | 适用 |
|---|---|---|
| 慢启动 | cwnd 每 RTT 翻倍（1→2→4→8…） | 连接刚建立/超时后探测带宽 |
| 拥塞避免 | cwnd 每 RTT +1（线性） | 接近 ssthresh 时试探 |
| 超时（RTO） | ssthresh=cwnd/2，cwnd=1 全盘重来 | 严重拥塞（报文可能被丢弃） |
| 快重传/快恢复 | 3 个重复 ACK → cwnd 砍半 +3，不归零 | 网络仍通，只丢了一段 |
| ECN（RFC 3168） | 路由器打 CE → 收方回 ECE → 发方减半回 CWR | 拥塞信号提前到达，不靠丢包 |
| SACK | 用块空隙只重传丢失段 | 高带宽高延迟链路收益巨大 |

**两个窗口别混：** rwnd（接收方告知，防淹没接收方）vs cwnd（发送方自维护，防淹没网络）；实际能发 = **min(cwnd, rwnd)**。

## 6. 粘包问题解决（TCP 字节流划帧）

| 方案 | 做法 | 适用 |
|---|---|---|
| 定长帧 | 每条消息固定长度 | 简单但浪费带宽 |
| 分隔符 | 消息间加 `\r\n` 等 | 文本协议（HTTP 头） |
| **长度头**（推荐） | `[4 字节长度][内容]`，累积解码 | 通用二进制协议（Netty LengthFieldBasedFrameDecoder） |
| HTTP 的做法 | Content-Length / chunked 切分响应 | HTTP/1.1 长连接复用 |

**决策规则：** 自定义 TCP 协议一律用长度头 + 累积缓冲解码器（正确处理粘包/拆包/半帧）；HTTP 场景靠 Content-Length/chunked，靠 EOF 兜底（HTTP/1.0）。

## 7. 寻址与规划（IP/CIDR）

| 要算什么 | 公式 | /24 例子 |
|---|---|---|
| 子网掩码 | 前 prefix 位为 1 | 255.255.255.0 |
| 网络地址 | IP & 掩码 | 192.168.1.0 |
| 广播地址 | 网络地址 或上主机位全 1 | 192.168.1.255 |
| 可用主机数 | 2^(32-prefix) − 2 | 254 台 |

**边界速记：** /30 可用 2（点对点）、/31 可用 2（RFC 3021 无网络/广播）、/32 可用 1（单主机）；私网三段 10/8、172.16/12、192.168/16；只能切小（VLSM）不能合并（超网是路由聚合的事）。

## 8. 一分钟决策速查

| 需求 | 选 |
|---|---|
| 传文件/网页/数据库（可靠有序） | TCP |
| 音视频/游戏/域名（低延迟可丢包） | UDP |
| 自定义 TCP 协议 | TCP + 长度头帧协议 |
| 加密传输 + 认证身份 | TCP + TLS（HTTPS） |
| 大包跨异构链路 | IP 分片，或 Path MTU Discovery 不分片 |
| 局域网内 IP→MAC | ARP（免费 ARP 做冲突检测） |
| 诊断网络（连通性/路径） | ICMP（ping / traceroute） |
| 域名→IP | DNS（UDP 53，大响应切 TCP 53） |
| 长肥网络吞吐优化 | TCP Window Scale + SACK + Timestamp |
| 服务端防连接泄漏 | 半开检测 + keep-alive 假死检测 |
| 企业网段规划 | CIDR/VLSM（切小不合并） |
