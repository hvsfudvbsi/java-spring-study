package com.study.mq.kafka;

import com.study.mq.config.KafkaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** KafkaProducerService 纯单元测试：只验证消息参数，不启动 Kafka。 */
class KafkaProducerServiceUnitTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaProducerService producerService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producerService = new KafkaProducerService(kafkaTemplate);
    }

    @Test
    void sendWithoutKeyShouldUseConfiguredTopic() {
        producerService.send("hello");

        verify(kafkaTemplate).send(KafkaConfig.TOPIC, "hello");
    }

    @Test
    void sendWithKeyShouldPreserveKeyAndMessage() {
        producerService.send("user-1", "hello");

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC), eq("user-1"), eq("hello"));
    }
}
