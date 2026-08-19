package com.study.designpattern.behavioral;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 观察者模式（Observer）用例（常用 + 不常用）
 *
 * 定义一对多依赖：一个对象（主题）状态变化时，自动通知所有依赖者（观察者）。
 * 适用：天气预报推送、公众号订阅、订单状态通知、Spring 事件驱动（@EventListener）。
 *
 * 推模型 vs 拉模型：
 *   推：主题把数据全量推给观察者（本示例）
 *   拉：观察者按需向主题要数据（getter 暴露）
 * 注意：JDK 自带的 java.util.Observable 已废弃，生产用 Guava EventBus 或 Spring 事件。
 */
public class ObserverDemo {

    /** 观察者接口 */
    public interface WeatherObserver {
        void update(float temperature, float humidity);
    }

    /** 主题：气象站（被观察者） */
    public static final class WeatherStation {
        private final List<WeatherObserver> observers = new ArrayList<>();
        private float temperature;
        private float humidity;

        public void register(WeatherObserver observer) {
            observers.add(observer);
        }

        public void unregister(WeatherObserver observer) {
            observers.remove(observer);
        }

        /** 数据更新 -> 通知所有观察者（推模型） */
        public void setMeasurements(float temperature, float humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
            System.out.println("  [气象站] 新数据: " + temperature + "°C, 湿度 " + humidity + "%");
            observers.forEach(o -> o.update(temperature, humidity));
        }

        public float temperature() {
            return temperature;
        }

        public float humidity() {
            return humidity;
        }
    }

    /** 具体观察者：手机 App */
    public static final class PhoneApp implements WeatherObserver {
        private final String name;

        public PhoneApp(String name) {
            this.name = name;
        }

        public void update(float temperature, float humidity) {
            System.out.println("    [App-" + name + "] 收到推送: " + temperature + "°C / " + humidity + "%");
        }
    }

    /** 具体观察者：户外大屏 */
    public static final class DisplayScreen implements WeatherObserver {
        public void update(float temperature, float humidity) {
            System.out.println("    [户外大屏] 展示: 当前 " + temperature + "°C");
        }
    }

    /** 不常用：函数式观察者（无需实现接口，直接注册回调） */
    public static final class MessageCenter {
        private final List<Consumer<String>> subscribers = new ArrayList<>();

        public void subscribe(Consumer<String> subscriber) {
            subscribers.add(subscriber);
        }

        public void publish(String message) {
            System.out.println("  [消息中心] 发布: " + message);
            subscribers.forEach(s -> s.accept(message));
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 观察者：常用写法（天气预报） ==========");
        WeatherStation station = new WeatherStation();
        PhoneApp app1 = new PhoneApp("小米");
        PhoneApp app2 = new PhoneApp("华为");
        DisplayScreen screen = new DisplayScreen();

        station.register(app1);
        station.register(app2);
        station.register(screen);
        station.setMeasurements(25.5f, 60);
        station.unregister(app2);          // 取消订阅后不再通知
        station.setMeasurements(26.0f, 58);

        System.out.println();
        System.out.println("========== 观察者：不常用写法（函数式订阅） ==========");
        MessageCenter center = new MessageCenter();
        center.subscribe(msg -> System.out.println("    短信订阅者收到: " + msg));
        center.subscribe(msg -> System.out.println("    邮件订阅者收到: " + msg));
        center.publish("双十一大促开始！");
    }
}
