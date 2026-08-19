package com.study.mq.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 配置：声明 topic
 *
 * Kafka 核心概念（面试必问）：
 *   Topic    主题：消息的分类（类似数据库表）
 *   Partition 分区：topic 的物理分片，支持并行和水平扩展
 *   Offset   偏移量：消费者在分区中的读取位置
 *   Consumer Group 消费组：组内消费者分摊分区（一个分区同一时刻只被组内一个消费者消费）
 *
 * 分区的作用：
 *   - 并行消费：分区数 = 最大并行度
 *   - 顺序保证：同一分区内消息有序
 *   - 扩展性：分区可以分布在不同 broker 上
 */
@Configuration
public class KafkaConfig {

    /** 学习用的 topic 名 */
    public static final String TOPIC = "study-topic";

    /**
     * NewTopic Bean：KafkaAdmin 启动时自动创建 topic（无需手动建）
     * partitions=1 简化学习；生产环境按吞吐量规划分区数
     */
    @Bean
    public NewTopic studyTopic() {
        return TopicBuilder.name(TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
