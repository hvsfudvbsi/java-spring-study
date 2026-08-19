package com.study.mq.kafka;

import com.study.mq.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 消费者：@KafkaListener 监听 topic
 *
 * 消费组概念：
 *   - 同一个 groupId 的多个消费者实例分摊分区（负载均衡）
 *   - 不同 groupId 可以各自独立消费同一份消息（广播）
 *
 * 消费语义（面试必问）：
 *   - at-most-once  最多一次（可能丢消息）
 *   - at-least-once 至少一次（可能重复，默认）
 *   - exactly-once  精确一次（配合事务）
 *
 * 手动确认（enable.auto.commit=false 时）：
 *   @KafkaListener 方法接收 Acknowledgment 参数，手动 ack 确认消息已处理
 */
@Component
@ConditionalOnProperty(name = "mq.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    /**
     * 监听 topic，自动提交偏移量（默认 at-least-once 语义）
     * 消费者启动后持续拉取消息，这里模拟业务处理
     */
    @KafkaListener(topics = KafkaConfig.TOPIC, groupId = "study-group")
    public void onMessage(String message) {
        log.info("<<< [Kafka 消费者:study-group] 收到消息: {}", message);
        // 这里写业务处理逻辑（如落库、调用下游）
    }
}
