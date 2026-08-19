package com.study.mq.kafka;

import com.study.mq.config.KafkaConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka 内嵌测试：@EmbeddedKafka 在测试中启动真实的内存版 Kafka broker，
 * 无需安装任何软件，开箱即用。
 *
 * 原理：
 *   - @EmbeddedKafka 注册 EmbeddedKafkaBroker 到测试上下文
 *   - Spring Boot 测试支持会自动把 spring.kafka.bootstrap-servers 指向内嵌 broker
 *   - 因此注入的 KafkaTemplate 直接连内嵌 broker
 *
 * 设计要点（避免测试间数据污染）：
 *   - 每个测试使用独立的 topic
 *   - 测试间共享同一个内嵌 broker，若共用 topic 会读到其他测试留下的消息
 */
@SpringBootTest(properties = "mq.rabbit.enabled=false") // 关闭 RabbitMQ，避免无环境时连接
@EmbeddedKafka(partitions = 1, topics = {KafkaConfig.TOPIC, "study-topic-key"})
class KafkaEmbeddedTest {

    /** 第二个测试专用的 topic，与第一个测试隔离 */
    private static final String KEY_TOPIC = "study-topic-key";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("生产消息后能从内嵌 broker 消费到")
    void sendAndConsume() {
        // 1. 生产消息（不带 key）
        kafkaTemplate.send(KafkaConfig.TOPIC, "hello-kafka").join();

        // 2. 用真实 Consumer 从内嵌 broker 拉取消息（验证消费链路）
        // 注意：KafkaTestUtils.consumerProps 第二个参数是 autoCommit(true/false)，
        // 且默认 key 反序列化器是 IntegerDeserializer，本测试 key 为 null 不受影响
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaConfig.TOPIC);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.TOPIC);
        consumer.close();

        assertNotNull(record, "应能消费到消息");
        assertEquals("hello-kafka", record.value());
    }

    @Test
    @DisplayName("带 key 发送：同 key 消息进入同一分区且保持顺序")
    void sendWithKey() {
        // 1. 同 key 发送两条消息（保证进入同一分区，保持顺序）
        kafkaTemplate.send(KEY_TOPIC, "user-1", "order-1").join();
        kafkaTemplate.send(KEY_TOPIC, "user-1", "order-2").join();

        // 2. 消费全部消息
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group-2", "true", embeddedKafkaBroker);
        // 覆盖 key 反序列化器：本测试的 key 是 String
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KEY_TOPIC);

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        ConsumerRecords<String, String> polled = KafkaTestUtils.getRecords(consumer);
        polled.forEach(records::add);
        consumer.close();

        // 3. 断言：同 key 进入同一分区，且保持发送顺序
        assertTrue(records.size() >= 2, "应消费到至少 2 条消息，实际: " + records.size());
        assertEquals("user-1", records.get(0).key());
        assertEquals("order-1", records.get(0).value());
        assertEquals("order-2", records.get(1).value());
    }
}
