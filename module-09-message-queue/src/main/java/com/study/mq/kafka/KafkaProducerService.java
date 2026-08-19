package com.study.mq.kafka;

import com.study.mq.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 生产者：通过 KafkaTemplate 发送消息
 *
 * 发送方式：
 *   send(topic, message)              不带 key（轮询分区）
 *   send(topic, key, message)         带 key（相同 key 进同一分区，保证顺序）
 *   send(topic, partition, key, message) 指定分区
 *
 * 可靠性（面试）：
 *   - acks=all：所有副本确认才算成功（最强可靠）
 *   - 重试：网络抖动自动重试
 *   - 幂等：enable.idempotence 防止重复写入
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 发送消息（不带 key） */
    public void send(String message) {
        log.info(">>> [Kafka 生产者] 发送消息到 topic={}: {}", KafkaConfig.TOPIC, message);
        kafkaTemplate.send(KafkaConfig.TOPIC, message);
    }

    /** 发送消息（带 key：相同 key 的消息保证进入同一分区，保持顺序） */
    public void send(String key, String message) {
        log.info(">>> [Kafka 生产者] 发送消息 topic={} key={}: {}", KafkaConfig.TOPIC, key, message);
        kafkaTemplate.send(KafkaConfig.TOPIC, key, message);
    }
}
