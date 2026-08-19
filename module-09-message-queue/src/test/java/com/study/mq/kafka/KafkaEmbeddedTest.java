package com.study.mq.kafka;

import com.study.mq.config.KafkaConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kafka 内嵌测试：@EmbeddedKafka 在测试中启动真实的内存版 Kafka broker，
 * 无需安装任何软件，开箱即用。
 *
 * 原理：
 *   - @EmbeddedKafka 注册 EmbeddedKafkaBroker 到测试上下文
 *   - Spring Boot 测试支持会自动把 spring.kafka.bootstrap-servers 指向内嵌 broker
 *   - 因此注入的 KafkaTemplate 直接连内嵌 broker
 *
 * 本测试验证完整的"生产 -> 消费"链路。
 */
@SpringBootTest(properties = "mq.rabbit.enabled=false") // 关闭 RabbitMQ，避免无环境时连接
@EmbeddedKafka(partitions = 1, topics = KafkaConfig.TOPIC)
class KafkaEmbeddedTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("生产消息后能从内嵌 broker 消费到")
    void sendAndConsume() {
        // 1. 生产消息
        kafkaTemplate.send(KafkaConfig.TOPIC, "hello-kafka").join();

        // 2. 用真实 Consumer 从内嵌 broker 拉取消息（验证消费链路）
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "earliest", embeddedKafkaBroker);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaConfig.TOPIC);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.TOPIC);
        consumer.close();

        assertNotNull(record, "应能消费到消息");
        assertEquals("hello-kafka", record.value());
    }

    @Test
    @DisplayName("带 key 发送：同 key 消息进入同一分区")
    void sendWithKey() {
        kafkaTemplate.send(KafkaConfig.TOPIC, "user-1", "order-1").join();
        kafkaTemplate.send(KafkaConfig.TOPIC, "user-1", "order-2").join();

        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group-2", "earliest", embeddedKafkaBroker);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaConfig.TOPIC);

        ConsumerRecord<String, String> r1 = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.TOPIC);
        ConsumerRecord<String, String> r2 = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.TOPIC);
        consumer.close();

        // 同 key 进入同一分区，且保持发送顺序
        assertEquals("user-1", r1.key());
        assertEquals("order-1", r1.value());
        assertEquals("order-2", r2.value());
    }
}
