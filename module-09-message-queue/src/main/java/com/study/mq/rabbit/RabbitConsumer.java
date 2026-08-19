package com.study.mq.rabbit;

import com.study.mq.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 消费者：@RabbitListener 监听队列
 * （默认关闭：mq.rabbit.enabled=false）
 *
 * 默认自动确认：消息交给消费者即确认（不管处理是否成功）。
 * 需要可靠处理时改为手动确认：
 *   @RabbitListener 方法接收 Channel + Message 参数，手动 basicAck/basicNack。
 *
 * 失败处理：
 *   - 简单重试：listener 抛出异常，Spring 自动重试（默认 3 次）
 *   - 死信队列：重试仍失败的消息进入死信队列（DLX），避免无限重试阻塞
 */
@Component
@ConditionalOnProperty(name = "mq.rabbit.enabled", havingValue = "true")
public class RabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitConsumer.class);

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(String message) {
        log.info("<<< [RabbitMQ 消费者] 收到消息: {}", message);
        // 这里写业务处理逻辑
    }
}
