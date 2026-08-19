package com.study.mq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消息队列学习模块入口
 *
 * 本模块同时演示两大主流消息中间件：
 *   Kafka      分布式流处理平台（发布订阅、分区消费、高吞吐）
 *   RabbitMQ   经典消息中间件（交换机路由、任务队列、可靠投递）
 *
 * 核心概念对比（面试必问）：
 *   - 模型：Kafka 是"日志分区"模型；RabbitMQ 是"交换机-队列"模型
 *   - 消费：Kafka 消费者主动拉取（pull）；RabbitMQ 队列推送给消费者（push）
 *   - 吞吐：Kafka 高吞吐（顺序写盘）；RabbitMQ 中吞吐（强可靠）
 *   - 顺序：Kafka 分区内有序；RabbitMQ 单队列有序
 *   - 场景：Kafka 适合日志/事件流/大数据；RabbitMQ 适合业务解耦/任务调度
 */
@SpringBootApplication
public class MessageQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageQueueApplication.class, args);
    }
}
