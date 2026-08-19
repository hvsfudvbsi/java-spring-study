# module-12-multithreading · 多线程与并发编程

> 纯 Java 模块（不依赖 Spring）。从 API 方法用例（常用 + 不常用）到四个完整并发实操：生产者-消费者、抢票、银行转账、高并发请求（虚拟线程）。
> Java 21。

## 📖 本模块内容

### 第一部分：API 方法用例（常用 + 不常用）

| 类 | 覆盖的 API |
|----|-----------|
| `apidemo/ThreadApiDemo` | **常用**：currentThread、start/join、sleep、interrupt、setDaemon、setName、getState；**不常用**：holdsLock、setPriority、yield、getStackTrace/getAllStackTraces、setUncaughtExceptionHandler、onSpinWait、已废弃的 stop/suspend/resume |
| `apidemo/RunnableCallableDemo` | **常用**：Runnable.run、Callable.call、Future.get、FutureTask、submit；**不常用**：get(超时)、cancel/isCancelled、isDone 轮询、自定义 ThreadFactory、defaultThreadFactory、ExecutionException.getCause |
| `apidemo/SynchronizedLockDemo` | **常用**：synchronized、ReentrantLock 的 lock/unlock/tryLock/lockInterruptibly、可重入；**不常用**：getHoldCount、isHeldByCurrentThread、getQueueLength、公平锁、Condition 多条件等待、ReentrantReadWriteLock、StampedLock 乐观读 |
| `apidemo/ExecutorApiDemo` | **常用**：Executors 快捷工厂、手写 ThreadPoolExecutor 7 参数、execute/submit/invokeAll/invokeAny、shutdown/awaitTermination；**不常用**：prestartCoreThread/prestartAllCoreThreads、allowCoreThreadTimeOut、setCorePoolSize 动态调参、getQueue/remove、setRejectedExecutionHandler、shutdownNow、purge、4 种拒绝策略 |
| `apidemo/AtomicApiDemo` | **常用**：get/set、incrementAndGet、addAndGet、compareAndSet；**不常用**：getAndSet/getAndAdd、updateAndGet/accumulateAndGet、lazySet、weakCompareAndSetPlain、AtomicReference、AtomicStampedReference 解决 ABA、LongAdder |
| `apidemo/ConcurrentCollectionDemo` | **常用**：ConcurrentHashMap 的 put/get/computeIfAbsent/putIfAbsent/forEach、CopyOnWriteArrayList；**不常用**：merge/compute、search/reduce、mappingCount、keySet 视图、CopyOnWriteArraySet、ConcurrentSkipListMap 有序查询 |
| `apidemo/BlockingQueueDemo` | **常用**：add/offer/put 与 remove/poll/take 四组方法、有界队列；**不常用**：offer/poll 超时版、remainingCapacity、drainTo、PriorityBlockingQueue、SynchronousQueue、LinkedTransferQueue.transfer、DelayQueue 自定义 Delayed |
| `apidemo/CompletableFutureApiDemo` | **常用**：supplyAsync/runAsync、thenApply/thenAccept/thenRun、thenCompose、thenCombine、allOf/anyOf、exceptionally/handle/whenComplete；**不常用**：applyToEither、orTimeout、completeOnTimeout、getNow、completeExceptionally/obtrudeValue、completedFuture/failedFuture、delayedExecutor、minimalCompletionStage、cancel |
| `apidemo/CoordinationApiDemo` | **常用**：CountDownLatch、CyclicBarrier、Semaphore、Phaser；**不常用**：await(超时)、getNumberWaiting/isBroken、acquire(n)/release(n)、drainPermits、公平信号量、Exchanger 交换数据、arriveAndDeregister |
| `apidemo/ThreadLocalApiDemo` | **常用**：set/get/remove、withInitial、initialValue；**不常用**：InheritableThreadLocal、线程池串数据坑、内存泄漏原理（弱引用 key）、ThreadLocalRandom |
| `apidemo/VirtualThreadApiDemo` | **常用**：Thread.startVirtualThread、Thread.ofVirtual().name、Executors.newVirtualThreadPerTaskExecutor、isVirtual；**不常用**：Thread.Builder 构建器、ofVirtual().factory()、newThreadPerTaskExecutor、unstarted、pinned 现象、平台线程 vs 虚拟线程吞吐对比 |

### 第二部分：并发实操示例

| 实操 | 文件 | 功能 | 技术点 |
|------|------|------|--------|
| 生产者-消费者 | `practice/ProducerConsumerDemo` | 订单处理系统：2 生产者下单、3 消费者履约，20 单不丢 | ArrayBlockingQueue 削峰、put/take 阻塞、优雅停机（poll 超时 + 完成信号） |
| 抢票系统 | `practice/TicketSaleDemo` | 100 张票 10 个窗口并发卖，对比三种写法；另附去掉 sleep 的错误写法变体与 Semaphore 限流写库 | 超卖 bug（check-then-act 非原子）、synchronized、AtomicInteger CAS 自旋、竞争窗口大小与超卖概率、Semaphore 模拟数据库连接池限流 |
| 银行转账 | `practice/BankTransferDemo` | 5 个账户 8 线程随机转账，验证"钱不丢、不死锁" | ReentrantLock、按账户 id 排序加锁防死锁、余额不足整笔失败、不变量校验、ConcurrentLinkedQueue 转账日志统计 |
| 高并发请求 | `practice/HighConcurrencyGatewayDemo` | 10,000 个并发请求模拟网关，对比固定线程池与虚拟线程吞吐 | 虚拟线程每请求一线程、阻塞自动让出 carrier、I/O 密集场景吞吐对比 |

## 🚀 运行方式

```bash
# 测试（含所有 API 与实操验证）
mvn test -pl module-12-multithreading

# 运行所有 API 方法用例
mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.Main
```

### 实操一：生产者-消费者（订单处理系统）
```bash
mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.ProducerConsumerDemo
```

### 实操二：抢票系统（三种写法对比）
```bash
mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.TicketSaleDemo
```

### 实操三：银行转账（完整并发项目）
```bash
mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.BankTransferDemo
```

### 实操四：高并发请求（虚拟线程 vs 固定线程池）
```bash
mvn compile exec:java -pl module-12-multithreading -Dexec.mainClass=com.study.multithreading.practice.HighConcurrencyGatewayDemo
```

## 🔍 核心概念讲解（面试必问）

### 1. 线程生命周期
```
NEW -> RUNNABLE -> BLOCKED / WAITING / TIMED_WAITING -> TERMINATED
```
- `start()` 只能调一次；`join()` 等待子线程结束
- `interrupt()` 是**协作式**中断：设置中断标志，由业务代码检查 `isInterrupted()` 自行退出
- 守护线程（Daemon）：所有非守护线程结束后 JVM 直接退出

### 2. synchronized vs Lock
| 维度 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 释放 | 自动（异常也释放） | 必须手动 unlock（finally） |
| 尝试获取 | 不支持 | tryLock |
| 中断响应 | 不支持 | lockInterruptibly |
| 公平性 | 非公平 | 可指定公平 |
| 条件变量 | wait/notify | newCondition 多条件 |

### 3. 线程池核心参数（构造器 7 参数）
```
corePoolSize（常驻） -> workQueue（排队） -> maximumPoolSize（扩容） -> 拒绝策略
```
- 执行流程：先核心线程，再任务队列，再非核心线程，满了走拒绝策略
- 生产建议手写 `ThreadPoolExecutor` + 有界队列 + 自定义线程工厂
- 关闭三部曲：`shutdown()` -> `awaitTermination()` -> 必要时 `shutdownNow()`

### 4. CAS 与原子类
- CAS（Compare-And-Swap）：`compareAndSet(期望值, 新值)`，失败重试（自旋）
- `incrementAndGet` 内部就是 CAS 循环，无锁但竞争激烈时性能下降（此时用 LongAdder）
- ABA 问题：值 A->B->A 但中间被改过，用 `AtomicStampedReference` 版本号解决

### 5. ThreadLocal 两大坑
1. **内存泄漏**：key 是弱引用、value 是强引用，线程池线程不回收 -> 必须 `finally { remove() }`
2. **串数据**：线程池复用线程，上一个任务的 ThreadLocal 值残留到下一个任务

### 6. 死锁四条件与破解
| 条件 | 破解 |
|------|------|
| 互斥 | 无法避免（锁的本质） |
| 占有且等待 | 一次性获取所有锁 |
| 不可剥夺 | 超时放弃（tryLock） |
| 循环等待 | **按固定顺序加锁**（银行转账按账户 id 排序，最简单有效） |

### 7. 虚拟线程（JDK 21，面试必问）

```
平台线程 = 1:1 映射 OS 线程（创建成本高、数量有限）
虚拟线程 = JVM 调度的轻量线程，挂在少量 carrier（平台线程）上，阻塞时自动让出
```

- **适用场景**：I/O 密集（网络、数据库、文件、sleep）-> 每任务一线程，代码简单吞吐高；CPU 密集无优势
- **创建**：`Thread.startVirtualThread(r)` / `Thread.ofVirtual().name("x").start(r)` / `Executors.newVirtualThreadPerTaskExecutor()`
- **pinned 坑**：虚拟线程在锁内做阻塞会占用 carrier（JDK 21 中 synchronized 与 ReentrantLock 都会 pin；JDK 24+ 的 ReentrantLock 已不 pin），避免锁内做阻塞 I/O
- **与 ThreadLocal**：每个虚拟线程有独立 ThreadLocal；虚拟线程默认是守护线程

## ✍️ 动手练习

1. 给 `ProducerConsumerDemo` 增加第 4 个消费者，观察吞吐变化；再把队列容量改成 1，观察阻塞。
2. ✅ 已实现：`TicketSaleDemo` 新增 `runWrongNoSleep`（去掉 sleep、10 万张票 20 窗口），运行可观察到大概率**不超卖**。原因：sleep 只是把"检查-扣减"的竞争窗口从毫秒级放大到可见程度；去掉后窗口缩到几纳秒，超卖变难复现，但 check-then-act 依然非原子，**bug 没有消失**——不超卖是概率/运气，修复必须靠 synchronized 或 CAS。
3. ✅ 已实现：`BankTransferDemo` 用 `ConcurrentLinkedQueue` 无锁并发追加所有成功转账（`transferLog()`），最后打印条数与累计金额。进阶：改成按账户分组打印"收支明细"。
4. 用 `CompletableFuture` 实现"并行查 3 个服务，全部成功才返回，任一失败走兜底"（提示：allOf + exceptionally）。
5. ✅ 已实现：`TicketSaleDemo.runSaleWithDb` 用 `Semaphore(3)` 模拟数据库连接池，同时最多 3 个窗口写库（实测最大并发写库 = 3）；对照不限流版本可看到并发写库冲到十几。核心：Semaphore 管"并发上限"，CAS/锁管"数据安全"——两个不同的问题。
6. 把 `HighConcurrencyGatewayDemo` 的请求数改成 100,000，观察虚拟线程与固定线程池的耗时差距是否拉大，并解释原因。
7. 用虚拟线程重写 `ProducerConsumerDemo` 的消费者，对比吞吐变化（提示：每订单一个虚拟线程）。
8. 在虚拟线程里用 `synchronized` 包住 `Thread.sleep`，观察整体吞吐下降（pinned 现象），再改成 `ReentrantLock` 对比（JDK 24+）。
