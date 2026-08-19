package com.study.mq.controller;

import com.study.mq.config.KafkaConfig;
import com.study.mq.kafka.KafkaProducerService;
import com.study.mq.rabbit.RabbitProducerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息发送触发接口：手动调用触发生产消息，观察消费者日志
 *
 * RabbitProducerService 是条件 Bean（mq.rabbit.enabled=true 才存在），
 * 所以用 ObjectProvider 注入，未启用时优雅降级。
 */
@RestController
@RequestMapping("/api/mq")
public class MqController {

    private final KafkaProducerService kafkaProducerService;
    private final ObjectProvider<RabbitProducerService> rabbitProducerProvider;

    public MqController(KafkaProducerService kafkaProducerService,
                        ObjectProvider<RabbitProducerService> rabbitProducerProvider) {
        this.kafkaProducerService = kafkaProducerService;
        this.rabbitProducerProvider = rabbitProducerProvider;
    }

    /** 发送到 Kafka：POST /api/mq/kafka?message=hello */
    @PostMapping("/kafka")
    public Map<String, String> sendToKafka(@RequestParam String message) {
        kafkaProducerService.send(message);
        return Map.of("message", "已发送到 Kafka topic  " + KafkaConfig.TOPIC);
    }

    /** 发送到 RabbitMQ：POST /api/mq/rabbit?message=hello（需 mq.rabbit.enabled=true） */
    @PostMapping("/rabbit")
    public Map<String, String> sendToRabbit(@RequestParam String message) {
        RabbitProducerService service = rabbitProducerProvider.getIfAvailable();
        if (service == null) {
            return Map.of("message", "RabbitMQ 未启用：请先配置环境并设置 mq.rabbit.enabled=true");
        }
        service.send(message);
        return Map.of("message", "已发送到 RabbitMQ exchange  " + com.study.mq.config.RabbitConfig.EXCHANGE);
    }
}
