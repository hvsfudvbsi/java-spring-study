package com.study.designpattern.behavioral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 中介者模式（Mediator）用例（常用 + 不常用）
 *
 * 用一个中介对象封装一组对象（同事）之间的交互，同事之间不再直接引用，降低耦合。
 * 适用：聊天室、机场塔台调度、MVC 中的 Controller（协调 Model 与 View）。
 *
 * 三者对比（面试高频）：
 *   观察者：一对多广播（发布者不知道订阅者是谁）
 *   中介者：多对多网状交互被收敛为"多对一"（同事 -> 中介者）
 *   外观  ：单向门面，子系统之间无交互
 */
public class MediatorDemo {

    // ---------- 中介者接口 ----------
    public interface ChatMediator {
        void sendMessage(User from, String message);

        void addUser(User user);
    }

    /** 具体中介者：聊天室 */
    public static final class ChatRoom implements ChatMediator {
        private final List<User> users = new ArrayList<>();

        @Override
        public void addUser(User user) {
            users.add(user);
            System.out.println("    [聊天室] " + user.name() + " 加入");
        }

        /** 转发给除发送者外的所有人 */
        @Override
        public void sendMessage(User from, String message) {
            users.stream()
                    .filter(u -> u != from)
                    .forEach(u -> u.receive(from.name() + ": " + message));
        }
    }

    /** 同事：用户（只认识中介者，不认识其他用户） */
    public static final class User {
        private final String name;
        private final ChatMediator mediator;
        private final List<String> inbox = new ArrayList<>();

        public User(String name, ChatMediator mediator) {
            this.name = name;
            this.mediator = mediator;
        }

        public String name() {
            return name;
        }

        public List<String> inbox() {
            return List.copyOf(inbox);
        }

        public void send(String message) {
            System.out.println("  " + name + " 发送: " + message);
            mediator.sendMessage(this, message);   // 通过中介者发，不直接找别人
        }

        public void receive(String message) {
            inbox.add(message);
            System.out.println("    " + name + " 收到: " + message);
        }
    }

    /** 不常用：函数式中介者（EventBus 风格：按事件类型注册消费者） */
    public static final class SimpleEventBus {
        private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();

        public <T> void subscribe(Class<T> type, Consumer<T> listener) {
            listeners.computeIfAbsent(type, k -> new ArrayList<>())
                    .add(event -> listener.accept(type.cast(event)));
        }

        @SuppressWarnings("unchecked")
        public void publish(Object event) {
            List<Consumer<Object>> consumers = listeners.get(event.getClass());
            if (consumers != null) {
                consumers.forEach(c -> c.accept(event));
            }
        }
    }

    public record OrderPlacedEvent(String orderId, double amount) {
    }

    public record OrderPaidEvent(String orderId) {
    }

    public static void main(String[] args) {
        System.out.println("========== 中介者：常用写法（聊天室） ==========");
        ChatRoom room = new ChatRoom();
        User alice = new User("Alice", room);
        User bob = new User("Bob", room);
        User carol = new User("Carol", room);
        room.addUser(alice);
        room.addUser(bob);
        room.addUser(carol);
        alice.send("大家好");
        bob.send("你好 Alice");

        System.out.println();
        System.out.println("========== 中介者：不常用写法（EventBus 风格） ==========");
        SimpleEventBus bus = new SimpleEventBus();
        bus.subscribe(OrderPlacedEvent.class,
                e -> System.out.println("    短信通知: 订单 " + e.orderId() + " 已下单 ¥" + e.amount()));
        bus.subscribe(OrderPaidEvent.class,
                e -> System.out.println("    邮件通知: 订单 " + e.orderId() + " 已支付"));
        bus.publish(new OrderPlacedEvent("ORD-1", 99.9));
        bus.publish(new OrderPaidEvent("ORD-1"));
    }
}
