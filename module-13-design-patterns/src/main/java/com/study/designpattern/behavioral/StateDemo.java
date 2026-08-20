package com.study.designpattern.behavioral;

import java.util.EnumMap;
import java.util.Map;

/**
 * 状态模式（State）用例（常用 + 不常用）
 *
 * 对象在内部状态改变时改变行为 —— 把"if/else 状态判断"拆成一个个状态类。
 * 适用：订单状态机、电梯、交通灯、TCP 连接状态。
 *
 * 与策略模式的区别（面试高频）：
 *   策略：对象行为可替换，状态由客户端选定，状态之间无关联
 *   状态：状态自动流转，当前状态决定行为，状态之间有关联（转移表）
 */
public class StateDemo {

    // ================= 常用写法：状态类 =================

    /** 状态接口 */
    public interface OrderState {
        String name();

        OrderState pay();      // 触发"支付"事件

        OrderState ship();     // 触发"发货"事件

        OrderState complete(); // 触发"完成"事件
    }

    /** 状态：待支付 */
    public static final class PendingPaymentState implements OrderState {
        @Override
        public String name() {
            return "待支付";
        }

        @Override
        public OrderState pay() {
            return new PaidState();
        }

        @Override
        public OrderState ship() {
            throw new IllegalStateException("待支付状态不能发货");
        }

        @Override
        public OrderState complete() {
            throw new IllegalStateException("待支付状态不能完成");
        }
    }

    /** 状态：已支付 */
    public static final class PaidState implements OrderState {
        @Override
        public String name() {
            return "已支付";
        }

        @Override
        public OrderState pay() {
            throw new IllegalStateException("不能重复支付");
        }

        @Override
        public OrderState ship() {
            return new ShippedState();
        }

        @Override
        public OrderState complete() {
            throw new IllegalStateException("未发货不能完成");
        }
    }

    /** 状态：已发货 */
    public static final class ShippedState implements OrderState {
        @Override
        public String name() {
            return "已发货";
        }

        @Override
        public OrderState pay() {
            throw new IllegalStateException("已支付状态不能再次支付");
        }

        @Override
        public OrderState ship() {
            throw new IllegalStateException("不能重复发货");
        }

        @Override
        public OrderState complete() {
            return new CompletedState();
        }
    }

    /** 状态：已完成（终态） */
    public static final class CompletedState implements OrderState {
        @Override
        public String name() {
            return "已完成";
        }

        @Override
        public OrderState pay() {
            throw new IllegalStateException("已完成订单不能操作");
        }

        @Override
        public OrderState ship() {
            throw new IllegalStateException("已完成订单不能操作");
        }

        @Override
        public OrderState complete() {
            throw new IllegalStateException("已完成订单不能操作");
        }
    }

    /** 上下文：订单（把行为委托给当前状态） */
    public static final class Order {
        private OrderState state = new PendingPaymentState();

        public void pay() {
            state = state.pay();
        }

        public void ship() {
            state = state.ship();
        }

        public void complete() {
            state = state.complete();
        }

        public String status() {
            return state.name();
        }
    }

    // ================= 不常用写法：枚举 + 转移表 =================

    public enum OrderStatus {PENDING, PAID, SHIPPED, COMPLETED}

    public enum OrderEvent {PAY, SHIP, COMPLETE}

    /** 枚举状态机：转移表定义所有合法流转，非法流转直接抛异常 */
    public static final class EnumStateMachine {
        // 转移表：当前状态 -> (事件 -> 目标状态)
        private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS =
                new EnumMap<>(OrderStatus.class);

        static {
            TRANSITIONS.put(OrderStatus.PENDING, Map.of(OrderEvent.PAY, OrderStatus.PAID));
            TRANSITIONS.put(OrderStatus.PAID, Map.of(OrderEvent.SHIP, OrderStatus.SHIPPED));
            TRANSITIONS.put(OrderStatus.SHIPPED, Map.of(OrderEvent.COMPLETE, OrderStatus.COMPLETED));
            TRANSITIONS.put(OrderStatus.COMPLETED, Map.of());   // 终态，无转移
        }

        public static OrderStatus next(OrderStatus current, OrderEvent event) {
            OrderStatus target = TRANSITIONS.get(current).get(event);
            if (target == null) {
                throw new IllegalStateException("非法状态转移: " + current + " + " + event);
            }
            return target;
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 状态：常用写法（状态类 + 委托） ==========");
        Order order = new Order();
        System.out.println("  初始状态: " + order.status());
        order.pay();
        System.out.println("  pay() 后: " + order.status());
        order.ship();
        System.out.println("  ship() 后: " + order.status());
        order.complete();
        System.out.println("  complete() 后: " + order.status());
        try {
            order.ship();   // 终态操作 -> 抛异常
        } catch (IllegalStateException e) {
            System.out.println("  终态继续操作被拦截: " + e.getMessage());
        }

        System.out.println();
        System.out.println("========== 状态：不常用写法（枚举 + 转移表） ==========");
        OrderStatus status = OrderStatus.PENDING;
        status = EnumStateMachine.next(status, OrderEvent.PAY);
        System.out.println("  转移表: PENDING + PAY -> " + status);
        status = EnumStateMachine.next(status, OrderEvent.SHIP);
        System.out.println("  转移表: PAID + SHIP -> " + status);
        status = EnumStateMachine.next(status, OrderEvent.COMPLETE);
        System.out.println("  转移表: SHIPPED + COMPLETE -> " + status);
        try {
            EnumStateMachine.next(OrderStatus.PENDING, OrderEvent.SHIP);
        } catch (IllegalStateException e) {
            System.out.println("  非法转移被拦截: " + e.getMessage());
        }
    }
}
