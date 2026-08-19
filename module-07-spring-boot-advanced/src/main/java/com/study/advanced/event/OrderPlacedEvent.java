package com.study.advanced.event;

/**
 * 领域事件：订单创建事件
 *
 * 事件驱动优势（解耦）：
 *   下单服务只管"发布事件"，不用知道谁会关心
 *   发短信、发邮件、更新库存等服务各自"监听事件"处理
 *   新增一个关心方，无需改动发布方代码
 */
public record OrderPlacedEvent(Long orderId, String customerName, double amount) {
}
