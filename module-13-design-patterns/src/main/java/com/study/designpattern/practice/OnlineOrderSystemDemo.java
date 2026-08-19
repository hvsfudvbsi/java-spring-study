package com.study.designpattern.practice;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实操示例一：在线商城下单系统（组合 5 个设计模式）
 *
 * 场景：下单 -> 支付 -> 发货，不同类型的订单（普通/秒杀/团购）流程相同但细节不同，
 *       支付方式可切换，订单状态变化要通知多个渠道（短信/邮件）。
 *
 * 用到的模式：
 *   工厂方法   : OrderFactory 按类型创建订单（秒杀 5 折、团购 8 折）
 *   策略       : PaymentStrategy 支付方式可运行期切换
 *   观察者     : OrderListener 订阅订单事件（下单/支付/发货时通知）
 *   状态       : Order 用"枚举 + 转移表"状态机约束合法流转
 *   模板方法   : OrderProcessor 固定下单流程骨架，子类只实现"创建订单"与"校验"钩子
 */
public class OnlineOrderSystemDemo {

    // ================= 状态（枚举 + 转移表） =================

    public enum OrderStatus {CREATED, PAID, SHIPPED, COMPLETED, CANCELLED}

    public enum OrderEvent {PAY, SHIP, COMPLETE, CANCEL}

    /** 转移表：当前状态 -> (事件 -> 目标状态) */
    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.CREATED, Map.of(
                OrderEvent.PAY, OrderStatus.PAID,
                OrderEvent.CANCEL, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.PAID, Map.of(
                OrderEvent.SHIP, OrderStatus.SHIPPED,
                OrderEvent.CANCEL, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.SHIPPED, Map.of(
                OrderEvent.COMPLETE, OrderStatus.COMPLETED));
        TRANSITIONS.put(OrderStatus.COMPLETED, Map.of());
        TRANSITIONS.put(OrderStatus.CANCELLED, Map.of());
    }

    // ================= 观察者 =================

    /** 观察者接口：订单事件监听器 */
    public interface OrderListener {
        void onOrderEvent(Order order, OrderEvent event);
    }

    public static final class SmsNotifier implements OrderListener {
        public void onOrderEvent(Order order, OrderEvent event) {
            System.out.println("    [短信] 订单 " + order.id() + " -> " + event);
        }
    }

    public static final class EmailNotifier implements OrderListener {
        public void onOrderEvent(Order order, OrderEvent event) {
            System.out.println("    [邮件] 订单 " + order.id() + " -> " + event);
        }
    }

    // ================= 订单（上下文：持有状态 + 观察者列表） =================

    public static final class Order {
        private final String id;
        private final String typeName;
        private final double originalAmount;
        private final double discount;                 // 折扣率
        private OrderStatus status = OrderStatus.CREATED;
        private final List<OrderListener> listeners = new ArrayList<>();
        private final List<OrderEvent> timeline = new ArrayList<>();

        Order(String id, String typeName, double originalAmount, double discount) {
            this.id = id;
            this.typeName = typeName;
            this.originalAmount = originalAmount;
            this.discount = discount;
        }

        public String id() {
            return id;
        }

        public String typeName() {
            return typeName;
        }

        /** 实付金额 = 原价 x 折扣 */
        public double amount() {
            return originalAmount * discount;
        }

        public OrderStatus status() {
            return status;
        }

        public List<OrderEvent> timeline() {
            return List.copyOf(timeline);
        }

        public void subscribe(OrderListener listener) {
            listeners.add(listener);
        }

        /** 状态转移 + 通知（观察者） */
        public void apply(OrderEvent event) {
            OrderStatus next = TRANSITIONS.get(status).get(event);
            if (next == null) {
                throw new IllegalStateException("非法状态转移: " + status + " + " + event);
            }
            status = next;
            timeline.add(event);
            listeners.forEach(l -> l.onOrderEvent(this, event));
        }
    }

    // ================= 工厂方法 =================

    public interface OrderFactory {
        Order create(double amount);

        String type();
    }

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    public static final class NormalOrderFactory implements OrderFactory {
        public Order create(double amount) {
            return new Order("N-" + SEQ.getAndIncrement(), "普通订单", amount, 1.0);
        }

        public String type() {
            return "普通";
        }
    }

    public static final class FlashOrderFactory implements OrderFactory {
        public Order create(double amount) {
            return new Order("F-" + SEQ.getAndIncrement(), "秒杀订单", amount, 0.5);
        }

        public String type() {
            return "秒杀";
        }
    }

    public static final class GroupOrderFactory implements OrderFactory {
        public Order create(double amount) {
            return new Order("G-" + SEQ.getAndIncrement(), "团购订单", amount, 0.8);
        }

        public String type() {
            return "团购";
        }
    }

    // ================= 策略 =================

    public interface PaymentStrategy {
        String pay(Order order);
    }

    public static final class WeChatStrategy implements PaymentStrategy {
        public String pay(Order order) {
            return "微信支付 ¥" + order.amount();
        }
    }

    public static final class AlipayStrategy implements PaymentStrategy {
        public String pay(Order order) {
            return "支付宝支付 ¥" + order.amount();
        }
    }

    // ================= 模板方法：下单流程 =================

    public abstract static class OrderProcessor {
        private final List<OrderListener> listeners;
        private final PaymentStrategy payment;

        protected OrderProcessor(List<OrderListener> listeners, PaymentStrategy payment) {
            this.listeners = listeners;
            this.payment = payment;
        }

        /** 固定骨架：创建 -> 订阅 -> 校验 -> 支付 -> 发货（final 不可覆写） */
        public final Order process(double amount) {
            Order order = createOrder(amount);            // 1. 抽象步骤：工厂创建订单
            listeners.forEach(order::subscribe);          // 2. 订阅通知（观察者）
            validate(order);                              // 3. 钩子：校验（默认不校验）
            order.apply(OrderEvent.PAY);                  // 4. 支付（状态流转 + 通知）
            System.out.println("    " + payment.pay(order));   // 5. 策略：具体支付方式
            order.apply(OrderEvent.SHIP);                 // 6. 发货（状态流转 + 通知）
            return order;
        }

        /** 抽象步骤：创建订单（子类决定用哪个工厂） */
        protected abstract Order createOrder(double amount);

        /** 钩子：校验（子类可覆写，如秒杀限购） */
        protected void validate(Order order) {
        }

        public abstract String name();
    }

    /** 普通订单处理器：无校验 */
    public static final class NormalOrderProcessor extends OrderProcessor {
        public NormalOrderProcessor(List<OrderListener> listeners, PaymentStrategy payment) {
            super(listeners, payment);
        }

        protected Order createOrder(double amount) {
            return new NormalOrderFactory().create(amount);
        }

        public String name() {
            return "普通下单流程";
        }
    }

    /** 秒杀订单处理器：覆写钩子，限购 ¥100 以内 */
    public static final class FlashOrderProcessor extends OrderProcessor {
        public FlashOrderProcessor(List<OrderListener> listeners, PaymentStrategy payment) {
            super(listeners, payment);
        }

        protected Order createOrder(double amount) {
            return new FlashOrderFactory().create(amount);
        }

        @Override
        protected void validate(Order order) {
            if (order.amount() > 100) {
                throw new IllegalStateException("秒杀订单限购 ¥100 以内（钩子校验拦截）");
            }
        }

        public String name() {
            return "秒杀下单流程";
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 在线商城下单系统（工厂+策略+观察者+状态+模板方法） ==========");

        List<OrderListener> listeners = List.of(new SmsNotifier(), new EmailNotifier());

        // 普通订单：微信支付
        OrderProcessor normal = new NormalOrderProcessor(listeners, new WeChatStrategy());
        Order order1 = normal.process(199);
        System.out.println("  " + normal.name() + " 完成: " + order1.id()
                + "[" + order1.typeName() + "] 实付 ¥" + order1.amount()
                + "，状态 " + order1.status());
        order1.apply(OrderEvent.COMPLETE);   // 再走一步：完成
        System.out.println("  确认收货后状态: " + order1.status());

        // 秒杀订单：支付宝支付（钩子校验限购）
        OrderProcessor flash = new FlashOrderProcessor(listeners, new AlipayStrategy());
        Order order2 = flash.process(80);
        System.out.println("  " + flash.name() + " 完成: " + order2.id()
                + "[" + order2.typeName() + "] 实付 ¥" + order2.amount()
                + "，状态 " + order2.status());

        try {
            new FlashOrderProcessor(listeners, new WeChatStrategy()).process(500);
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage());
        }

        // 非法状态转移演示
        try {
            order1.apply(OrderEvent.SHIP);   // 已 COMPLETED，不能再发货
        } catch (IllegalStateException e) {
            System.out.println("  非法转移被拦截: " + e.getMessage());
        }
    }
}
