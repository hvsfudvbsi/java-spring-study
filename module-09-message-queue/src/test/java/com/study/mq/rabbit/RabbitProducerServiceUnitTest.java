package com.study.mq.rabbit;

import com.study.mq.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** RabbitProducerService 纯单元测试：不要求 RabbitMQ 环境。 */
class RabbitProducerServiceUnitTest {

    private RabbitTemplate rabbitTemplate;
    private RabbitProducerService producerService;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        producerService = new RabbitProducerService(rabbitTemplate);
    }

    @Test
    @DisplayName("发送消息使用配置的交换机与路由键")
    void sendShouldUseConfiguredExchangeAndRoutingKey() {
        producerService.send("hello-rabbit");

        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, "hello-rabbit");
    }
}
