package com.study.mq.rabbit;

import com.study.mq.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 生产者：通过 RabbitTemplate 发送消息
 * （默认关闭：mq.rabbit.enabled=false）
 *
 * 发送流程：RabbitTemplate.convertAndSend(exchange, routingKey, message)
 *   -> 消息进交换机 -> 按绑定规则路由到队列 -> 消费者接收
 *
 * 可靠性保障：
 *   - 消息确认（Confirm）：发送后回调确认 broker 已收到
 *   - 消息持久化：队列 durable + 消息 persistent
 *   - 死信队列：消息处理失败进入死信队列，便于排查重试
 */
@Service
@ConditionalOnProperty(name = "mq.rabbit.enabled", havingValue = "true")
public class RabbitProducerService {

    private static final Logger log = LoggerFactory.getLogger(RabbitProducerService.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(String message) {
        log.info(">>> [RabbitMQ 生产者] 发送到 exchange={} routingKey={}: {}",
                RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
    }
}
