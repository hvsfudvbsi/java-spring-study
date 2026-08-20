package com.study.mq.controller;

import com.study.mq.kafka.KafkaProducerService;
import com.study.mq.rabbit.RabbitProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MqController 纯单元测试：不启动消息中间件。 */
class MqControllerUnitTest {

    private KafkaProducerService kafkaProducerService;
    private ObjectProvider<RabbitProducerService> rabbitProvider;
    private MqController controller;

    @BeforeEach
    void setUp() {
        kafkaProducerService = mock(KafkaProducerService.class);
        rabbitProvider = mock(ObjectProvider.class);
        controller = new MqController(kafkaProducerService, rabbitProvider);
    }

    @Test
    void sendToKafkaShouldDelegateAndReturnTopicMessage() {
        var result = controller.sendToKafka("hello");

        verify(kafkaProducerService).send("hello");
        assertThat(result.get("message")).contains("study-topic");
    }

    @Test
    void sendToRabbitShouldExplainWhenRabbitIsDisabled() {
        when(rabbitProvider.getIfAvailable()).thenReturn(null);

        var result = controller.sendToRabbit("hello");

        assertThat(result.get("message")).contains("RabbitMQ 未启用");
    }

    @Test
    void sendToRabbitShouldDelegateWhenRabbitIsAvailable() {
        RabbitProducerService rabbitService = mock(RabbitProducerService.class);
        when(rabbitProvider.getIfAvailable()).thenReturn(rabbitService);

        var result = controller.sendToRabbit("hello");

        verify(rabbitService).send("hello");
        assertThat(result.get("message")).contains("study.exchange");
    }
}
