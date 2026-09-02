# module-17-high-concurrency — 高并发实战

> 纯 Java 模块（不依赖 Spring/Netty），Java 21 + 虚拟线程。从「线程池调优、TPS 提升、
> 限流熔断、系统稳定性」四个专题出发，每节都有**可运行的 Demo + 确定性测试**，最后用压测
> 对比把各技巧量化出来。适合学完 module-12 多线程后，进阶理解「系统层面如何扛住高并发」。

## 一、模块目标与前置知识

- 目标：把散碎的并发 API 提升为**系统化高并发设计能力**——什么时候调线程池、怎么提 TPS、
  怎么防雪崩（限流/熔断/隔离/超时/背压），以及每种手段的取舍。
- 前置：Java 线程/锁/线程池基础（module-12）、基本网络与 I/O 概念；限流算法需要一点概率/时间概念。

## 二、知识点目录与源码映射

| 专题 | 类 | 覆盖内容 |
|------|----|---------|
| 线程池调优 | `pool/DynamicThreadPool` | **三个参数联动**（核心→队列→扩容→拒绝）、**运行期动态调参**（可扩容队列）、监控快照（活跃/积压/拒绝/最大池）、预热、优雅停机 |
| 线程池调优 | `pool/ThreadPoolBestPractices` | 线程**命名**（jstack 定位）、**未捕获异常兜底**、四种拒绝策略对比、**核心线程超时回收** |
| TPS 提升 | `tps/BatchProcessor` | 批量 vs 逐条：固定开销摊薄、批大小权衡、批量失败连坐 |
| TPS 提升 | `tps/ZeroCopyDemo` | **零拷贝** `FileChannel.transferTo` vs 传统 read/write，内容一致性校验 + 耗时对比 |
| TPS 提升 | `tps/SimpleConnectionPool` | 连接池**复用**：信号量限额 + 空闲队列、acquire 超时、坏连接废弃、复用率统计 |
| 限流 | `ratelimit/TokenBucket` | **令牌桶**：懒补发、容量=突发额度、阻塞式获取 |
| 限流 | `ratelimit/LeakyBucket` | **漏桶**：恒定输出速率、桶满丢弃（削峰） |
| 限流 | `ratelimit/SlidingWindowCounter` | **精确滑动窗口**：时间戳列表、窗口滑动、边界无跳变 |
| 熔断 | `ratelimit/CircuitBreaker` | 三态机：**Closed → Open → Half-Open**、连续失败阈值、冷却期、半开试探、快速失败计数 |
| 稳定性 | `stability/GracefulShutdownDemo` | `shutdown()` 排空队列 vs `shutdownNow()` 中断+退回，配合 awaitTermination |
| 稳定性 | `stability/BulkheadExecutor` | **资源隔离（隔舱）**：每依赖独立线程池，一个舱进水不沉船 |
| 稳定性 | `stability/TimeoutControl` | **超时控制**：`Future.get(timeout)` + `cancel(true)`，虚拟线程版；可中断 I/O 的重要性 |
| 稳定性 | `stability/BackpressureDemo` | **背压**：有界队列阻塞 put（内存有界）、信号量 in-flight 限流 |
| 压测对比 | `bench/BenchmarkDemo` | 单线程 vs 线程池 vs 虚拟线程、批量 vs 逐条、连接池 vs 每请求新建 |
| 总入口 | `Main` | 依次运行全部专题演示 |

测试：`src/test/java/com/study/concurrency/**` 共 **52 个**（限流/熔断用假时钟做成确定性断言，不 sleep 等真实秒数）。

## 三、运行方式

```bash
# 运行全部专题演示（约十几秒，含少量真实等待）
mvn compile exec:java -pl module-17-high-concurrency -Dexec.mainClass=com.study.concurrency.Main

# 压测对比主程序
mvn compile exec:java -pl module-17-high-concurrency -Dexec.mainClass=com.study.concurrency.bench.BenchmarkDemo

# 只跑某个专题（如限流）
mvn compile exec:java -pl module-17-high-concurrency -Dexec.mainClass=com.study.concurrency.ratelimit.TokenBucket

# 运行全部测试
mvn test -pl module-17-high-concurrency
```

## 四、核心概念

### 1. 线程池参数联动（面试必考）

`ThreadPoolExecutor` 收到任务的路径：**核心线程没满 → 直接开新线程执行；核心线程全忙 → 进队列；
队列满了 → 把线程数扩到 max；max 也满了 → 走拒绝策略。**

- 因此「队列设得足够大」时 max 形同虚设；「max 设得很大但队列很小」则线程数会频繁扩缩。
- 动态调参：核心/最大可 `setCorePoolSize/setMaximumPoolSize`；队列换不掉，只能改**容量语义**——
  本模块的 `ResizableLinkedQueue` 用 volatile 容量 + offer 判断实现了运行期扩缩队列。
- 监控：`getActiveCount`（瞬时活跃）、`getQueue().size()`（积压）、`getCompletedTaskCount`（吞吐）、
  `getLargestPoolSize`（历史峰值）组合出「该不该扩容」的判断。

### 2. 四种拒绝策略（`ThreadPoolBestPractices.rejectionDemo` 逐个看）

| 策略 | 行为 | 适用 |
|------|------|------|
| `AbortPolicy`（默认） | 抛 `RejectedExecutionException` | 坚决不丢任务，调用方必须处理 |
| `CallerRunsPolicy` | **调用方线程**直接执行被拒任务 | 天然背压：调用方自己被拖慢 |
| `DiscardPolicy` | 静默丢弃 | 可丢的任务（日志采样等） |
| `DiscardOldestPolicy` | 丢弃队头最旧任务 | 更重视「最新任务」的场景 |

### 3. 限流三剑客的取舍

| 算法 | 能否突发 | 输出 | 实现代价 | 典型场景 |
|------|---------|------|---------|---------|
| 令牌桶 | 能（容量=突发额度） | 可突增 | 低（懒补发） | 秒杀放量、API 网关默认 |
| 漏桶 | 否 | **恒定** | 低 | 下游脆弱系统（短信/老库） |
| 滑动窗口 | 否 | 平滑 | 中（时间戳列表） | 接口配额、防刷 |

关键点：三种都可用**懒计算**（来请求时按时间差补发/漏水/剔除过期），不用后台定时器；
生产上用**单调时钟**（`System.nanoTime`）避免系统改时间导致的误判。

### 4. 熔断器三态流转

```
Closed ──连续失败≥阈值──▶ Open ──冷却期到、放试探──▶ Half-Open
Half-Open ──试探成功──▶ Closed    Half-Open ──试探失败──▶ Open(冷却重计)
```

- Open 期间**快速失败**（不进真实调用），防止线程/队列被故障下游拖死。
- Half-Open 只放**少量并发试探**（本模块默认 1），试探多了等于没熔断。
- 成功必须**复位连续失败计数**，否则恢复后残留计数会误熔断。

### 5. 稳定性四件套

- **优雅停机**：先 `shutdown()` 拒绝新任务并排空队列，`awaitTermination(超时)` 等待存量完成；
  `shutdownNow()` 中断在跑任务并返回排队任务——发版/缩容用前者，强制下线才用后者。
- **资源隔离**：每个依赖独立小线程池，慢依赖占满自己的池即可，其他依赖不受影响（对比没有隔舱时的大池雪崩）。
- **超时控制**：所有外部调用必须设超时；超时后 `future.cancel(true)` 发中断停止底层调用；
  I/O 实现要可中断（`socket.setSoTimeout`、HTTP 读超时），否则 cancel 只是摆设。
- **背压**：有界队列 + 阻塞 `put`（内存有界）或信号量 in-flight 限流；永远不要用无界队列承接生产洪峰。

### 6. 零拷贝与连接池复用

- **零拷贝**：`transferTo` 让数据在内核态直接搬运（DMA），省掉用户态中转的多轮拷贝与上下文切换；
  Kafka/`FileRegion` 同理；单次上限 2GB-1，大文件要循环调用。
- **连接池**：借还租赁 + 超时等待 + 坏连接废弃；把「每请求建连」（TCP 握手+TLS+认证 ≈ 几十 ms）摊到数千次请求上。
- **批量**：固定开销摊薄（RTT/事务/建连只剩一次），但批太大单次耗时/内存/失败连坐都上升，需要权衡。

## 五、常见错误与排查

1. **无界队列接洪峰** → OOM。用 `ArrayBlockingQueue`/`LinkedBlockingQueue(容量)` + 监控积压。
2. **线程池没命名** → jstack 全是 `pool-1-thread-1`，线上没法定位。自定义 ThreadFactory 加前缀。
3. **任务抛异常静默消失** → 给线程设 `UncaughtExceptionHandler`，异常要有名字有记录。
4. **不区分 shutdown/shutdownNow** → 强停丢在途任务造成数据不一致；要能排空就排空。
5. **外部调用不设超时** → 慢下游无限占线程，线程池被打满=雪崩入口。超时后记得 `cancel(true)`。
6. **限流器用固定窗口** → 窗口边界双倍突发；量级不够精细时用滑动窗口。
7. **超时时间设错**：网关超时 ≠ 内部调用超时，每一跳都要自己的超时。
8. **限流/熔断测试用真 sleep 等时间** → 慢且偶发；注入假时钟（本模块 `testutil/FakeClock`）秒变确定性。

## 六、测试分类

- **限流/熔断**（22 个）：`FakeClock` 快进，断言「满桶/补发/封顶/窗口滑动/半开恢复」等边界，零真实等待。
- **线程池**（7 个）：用 `CountDownLatch` 把工作线程「钉住」再投任务，让饱和顺序/拒绝/扩容成为确定性断言。
- **TPS**（13 个）：批量正确性+更快、零拷贝内容逐字节一致、连接池创建次数/复用率/超时/坏连接。
- **稳定性**（10 个）：shutdown 排空 vs 退回、隔舱互不拖累、超时+中断送达（轮询 2s 容忍 cancel 异步）、背压任务一个不丢。
- 每个测试类名带 `@DisplayName`，场景和预期都写在名字里，可直接当学习材料读。

## 七、动手练习

1. 给 `DynamicThreadPool` 增加 `DiscardOldest` 模式并写测试：观察「最早排队任务被丢」的行为。
2. 给 `TokenBucket` 增加「批量扣减 + 不足等齐」的 `acquire(n)` 保证原子性测试。
3. 用 `SlidingWindowCounter` 思想实现**分桶近似版**（如 1 分钟切成 12 个 5 秒桶），对比内存占用。
4. 给 `CircuitBreaker` 增加「滑动窗口失败率」（最近 10 秒失败率 > 50% 才熔断），替代连续计数。
5. 把 `SimpleConnectionPool` 的「坏连接」策略改成「归还时校验+超时淘汰」并补测试。
6. 用虚拟线程重写 `BackpressureDemo` 的生产者，观察虚拟线程下背压语义是否有变化。
7. 扩展 `BenchmarkDemo`：加入「批量 + 连接池」组合对比，观察叠加效果。

## 八、已知限制

- 动态队列的 `offer` 用 `synchronized` 简化了并发语义（容量修改与入队竞争），生产级实现可换
  CAS + 信号量；对学习演示足够。
- Demo 里的「创建连接 2ms」「批量固定开销 1ms」是模拟值，真实收益取决于 I/O 成本占比；
  压测对比是演示性质，未做 JIT 预热/多次取中位数。
- `CircuitBreaker` 半开状态只限制并发数，未限制**试探窗口时长**（生产常用时间窗口内统计成功率）。
- 超时取消依赖任务自身响应中断；遇到忽略中断的 I/O 库需要在调用方换可中断实现。
- 涉及真实时间的演示（熔断冷却、限流 demo）按秒级等待，完整演示约十几秒属正常。