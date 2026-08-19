package com.study.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明交换机、队列、绑定
 * （默认关闭：mq.rabbit.enabled=false，配置好环境后改为 true）
 *
 * RabbitMQ 核心概念（面试必问）：
 *   Producer -> Exchange（交换机）-> Binding（绑定规则）-> Queue -> Consumer
 *
 * 交换机类型：
 *   DirectExchange   直连：routingKey 精确匹配
 *   TopicExchange    主题：routingKey 通配匹配（* 匹配一个词，# 匹配零或多个词）
 *   FanoutExchange   广播：忽略 routingKey，发给所有绑定队列
 *   HeadersExchange  头部：按消息头匹配
 *
 * 消息确认机制：
 *   自动确认（默认）：消费者收到即确认
 *   手动确认：处理成功才 ack，失败可 nack/重回队列（防止消息丢失）
 */
@Configuration
@ConditionalOnProperty(name = "mq.rabbit.enabled", havingValue = "true")
public class RabbitConfig {

    public static final String EXCHANGE = "study.exchange";
    public static final String QUEUE = "study.queue";
    public static final String ROUTING_KEY = "study.routing";

    /** 持久化队列：durable=true，重启后队列还在 */
    @Bean
    public Queue studyQueue() {
        return new Queue(QUEUE, true);
    }

    /** Topic 交换机 */
    @Bean
    public TopicExchange studyExchange() {
        return new TopicExchange(EXCHANGE);
    }

    /** 绑定：队列 <-> 交换机，routingKey 匹配规则 */
    @Bean
    public Binding binding(Queue studyQueue, TopicExchange studyExchange) {
        return BindingBuilder.bind(studyQueue).to(studyExchange).with(ROUTING_KEY);
    }
}
