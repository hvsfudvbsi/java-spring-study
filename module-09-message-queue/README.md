# module-09-message-queue · 消息队列（Kafka + RabbitMQ）

> 学习两大主流消息中间件：**Kafka**（开箱即用，内嵌 broker 零依赖）和 **RabbitMQ**（配置项保留，按需启用）。

## 📖 本模块知识点

| 文件 | 知识点 |
|------|--------|
| `kafka/KafkaProducerService` | `KafkaTemplate` 生产消息、带 key 保证分区顺序 |
| `kafka/KafkaConsumer` | `@KafkaListener` 消费、消费组、消费语义 |
| `config/KafkaConfig` | Topic/Partition/Offset、`NewTopic` 自动建 topic |
| `rabbit/RabbitProducerService` | `RabbitTemplate` 发送、交换机路由 |
| `rabbit/RabbitConsumer` | `@RabbitListener` 监听队列、确认机制 |
| `config/RabbitConfig` | 交换机/队列/绑定声明、四种交换机类型 |
| `controller/MqController` | 手动触发发送的接口 |
| `KafkaEmbeddedTest` | `@EmbeddedKafka` 内嵌 broker 测试（零依赖） |

## 🚀 运行与测试

### Kafka（开箱即用）

```bash
# 测试：启动内嵌 Kafka broker，验证完整生产-消费链路（无需安装任何软件）
mvn test -pl module-09-message-queue

# 主应用运行（需要可访问的 Kafka broker）
# 没有 Kafka 的话可用 Docker 快速起一个：
#   docker run -d -p 9092:9092 apache/kafka:latest
mvn spring-boot:run -pl module-09-message-queue

# 触发生产消息，观察消费者日志
curl -X POST "http://localhost:8080/api/mq/kafka?message=你好Kafka"
```

### RabbitMQ（配置项保留，按需启用）

RabbitMQ **没有纯内嵌方案**，需要真实 broker。启用步骤：

```bash
# 1. 安装并启动 RabbitMQ（Docker 方式最省事）
#    docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# 2. 修改 application.yml
#    mq.rabbit.enabled: false  ->  true

# 3. 启动应用并触发
mvn spring-boot:run -pl module-09-message-queue
curl -X POST "http://localhost:8080/api/mq/rabbit?message=你好RabbitMQ"
```

> RabbitMQ 管理界面：http://localhost:15672 （guest/guest），可查看队列和消息。

## 🔍 核心概念讲解

### Kafka vs RabbitMQ 对比（面试必问）

| 维度 | Kafka | RabbitMQ |
|------|-------|----------|
| 模型 | Topic + Partition（日志分区） | Exchange + Queue（路由） |
| 消费方式 | 消费者主动拉取（pull） | broker 推送给消费者（push） |
| 吞吐量 | 极高（顺序写盘） | 中（强可靠） |
| 消息顺序 | 分区内有序 | 单队列有序 |
| 消息删除 | 按保留时间/大小（消费后仍在） | 消费后即删除 |
| 适用场景 | 日志、事件流、大数据 | 业务解耦、任务队列、可靠投递 |

### Kafka 核心概念
- **Topic**：消息分类；**Partition**：topic 的分片，分区内有序、分区数=最大并行度
- **Offset**：消费者在分区内的读取位置
- **Consumer Group**：组内消费者分摊分区（一个分区同一时刻只被组内一个消费者消费）

### RabbitMQ 核心概念
- **Exchange 四种类型**：Direct（精确匹配）/ Topic（通配 `*` `#`）/ Fanout（广播）/ Headers
- **消息确认**：自动确认（默认）vs 手动确认（`basicAck`/`basicNack`）
- **可靠性**：队列 durable + 消息持久化 + 死信队列（DLX）

## ✍️ 动手练习

1. 把 Kafka 的 `studyTopic` 分区数改为 3，用 `send(key, message)` 发多条同 key 消息，观察分区分配。
2. 给 `KafkaConsumer` 增加手动确认（`Acknowledgment` 参数 + `enable.auto.commit=false`）。
3. 为 RabbitMQ 增加一个 Fanout 交换机 + 两个队列，演示广播。
4. 配置 RabbitMQ 死信队列：消费者处理失败的消息自动进入 DLX。
5. 对比测试：分别用 Kafka 和 RabbitMQ 发送 1000 条消息，观察吞吐差异。
