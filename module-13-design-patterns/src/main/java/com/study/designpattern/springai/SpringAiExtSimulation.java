package com.study.designpattern.springai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 【模拟】复刻 spring-ai-client-chat-1.1.8.jar 的扩展机制（纯 Java，无任何外部依赖）
 *
 * 覆盖 3 种设计模式，一一对应 jar 中的真实类：
 *
 *   1. 适配器 Adapter —— Builder.defaultTools(Object...) -> ToolCallbackUtils -> ToolCallback
 *                       （把 @Tool 注解的普通 Java 方法，适配成 LLM 可调用的工具）
 *   2. 策略 Strategy  —— ChatClientCustomizer.customize(Builder)
 *                       （Spring Boot 收集所有 Customizer Bean 依次定制，每个 Customizer 是一种策略）
 *   3. 观察者 Observer —— ChatClientCompletionObservationHandler 等
 *                       （Micrometer Observation 体系：调用事件发布，Handler 订阅处理）
 *
 * 运行：java com.study.designpattern.springai.SpringAiExtSimulation
 */
public final class SpringAiExtSimulation {

    // ============================================================
    // 1. 适配器 Adapter：把任意 @Tool 方法适配成模型可调用的工具
    // ============================================================

    /** 工具注解（对应 spring-ai 的 @Tool） */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tool {
        String name() default "";

        String description() default "";
    }

    /** 适配目标：模型可调用的工具接口（对应 jar 依赖的 ToolCallback） */
    public interface ToolCallback {
        String toolName();

        String toolDescription();

        /** 执行工具 */
        String call(String arguments);
    }

    /** 适配器：把带 @Tool 注解的方法包装成 ToolCallback（对应 ToolCallbackUtils） */
    static final class ToolCallbackAdapter {
        static ToolCallback adapt(Object target, Method method, Tool annotation) {
            String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
            String description = annotation.description();
            return new ToolCallback() {
                @Override
                public String toolName() {
                    return name;
                }

                @Override
                public String toolDescription() {
                    return description;
                }

                @Override
                public String call(String arguments) {
                    try {
                        Object result = method.invoke(target, arguments);
                        return String.valueOf(result);
                    } catch (ReflectiveOperationException e) {
                        return "工具执行失败: " + e.getCause();
                    }
                }
            };
        }
    }

    /** 工具注册中心：扫描对象上的 @Tool 方法并适配注册 */
    static final class ToolRegistry {
        private final Map<String, ToolCallback> callbacks = new LinkedHashMap<>();

        ToolRegistry register(Object target) {
            for (Method method : target.getClass().getDeclaredMethods()) {
                Tool annotation = method.getAnnotation(Tool.class);
                if (annotation != null) {
                    ToolCallback callback = ToolCallbackAdapter.adapt(target, method, annotation);
                    callbacks.put(callback.toolName(), callback);
                    System.out.println("  [适配器] @Tool 方法 " + method.getName() + " -> 工具 \"" + callback.toolName() + "\"");
                }
            }
            return this;
        }

        ToolCallback get(String name) {
            return callbacks.get(name);
        }

        List<String> names() {
            return new ArrayList<>(callbacks.keySet());
        }
    }

    /** 被适配对象：一个"什么都不知道"的普通业务类，方法带 @Tool 注解 */
    static final class CalculatorService {
        @Tool(name = "calculator_add", description = "两个整数相加")
        public String add(String arguments) {
            String[] parts = arguments.split("[+,，]");
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            return (a + b) + "";
        }

        @Tool(name = "calculator_multiply", description = "两个整数相乘")
        public String multiply(String arguments) {
            String[] parts = arguments.split("[*,×]");
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            return (a * b) + "";
        }
    }

    // ============================================================
    // 2. 策略 Strategy：ChatClientCustomizer 可插拔定制
    // ============================================================

    /** 策略接口（对应 jar 的 ChatClientCustomizer） */
    public interface ChatClientCustomizer {
        void customize(SpringAiCoreSimulation.Builder builder);
    }

    /** 策略 1：给客户端加日志 Advisor */
    static final class LoggingCustomizer implements ChatClientCustomizer {
        @Override
        public void customize(SpringAiCoreSimulation.Builder builder) {
            builder.defaultAdvisors(new SpringAiCoreSimulation.LoggerAdvisor());
        }
    }

    /** 策略 2：设置默认系统提示词 */
    static final class SystemPromptCustomizer implements ChatClientCustomizer {
        private final String prompt;

        SystemPromptCustomizer(String prompt) {
            this.prompt = prompt;
        }

        @Override
        public void customize(SpringAiCoreSimulation.Builder builder) {
            builder.defaultSystem(prompt);
        }
    }

    /** 策略应用器：模拟 Spring Boot 自动配置 —— 收集所有 Customizer 依次应用 */
    static final class CustomizerApplier {
        static SpringAiCoreSimulation.ChatClient apply(SpringAiCoreSimulation.ChatModel model,
                                                       List<ChatClientCustomizer> customizers) {
            SpringAiCoreSimulation.Builder builder = SpringAiCoreSimulation.ChatClient.builder(model);
            for (ChatClientCustomizer customizer : customizers) {
                System.out.println("  [策略] 应用 " + customizer.getClass().getSimpleName());
                customizer.customize(builder);
            }
            return builder.build();
        }
    }

    // ============================================================
    // 3. 观察者 Observer：调用事件发布 + Handler 订阅
    // ============================================================

    /** 事件对象（对应 Micrometer Observation.Context） */
    public record CallEvent(String type, String userPrompt, long timestamp) {
    }

    /** 观察者接口（对应 ObservationHandler） */
    public interface ObservationHandler extends Consumer<CallEvent> {
    }

    /** 观察者 1：完成埋点（对应 ChatClientCompletionObservationHandler） */
    static final class CompletionObservationHandler implements ObservationHandler {
        @Override
        public void accept(CallEvent event) {
            if ("COMPLETED".equals(event.type())) {
                System.out.println("  [观察者 CompletionHandler] 埋点：调用完成，prompt="
                        + event.userPrompt() + "，时间=" + event.timestamp());
            }
        }
    }

    /** 观察者 2：提示词内容埋点（对应 ChatClientPromptContentObservationHandler） */
    static final class PromptContentObservationHandler implements ObservationHandler {
        @Override
        public void accept(CallEvent event) {
            if ("STARTED".equals(event.type())) {
                System.out.println("  [观察者 PromptContentHandler] 埋点：请求开始，prompt="
                        + event.userPrompt());
            }
        }
    }

    /** 事件注册中心（对应 ObservationRegistry）：发布事件 -> 通知所有订阅者 */
    static final class ObservationRegistry {
        private final List<ObservationHandler> handlers = new ArrayList<>();

        ObservationRegistry addHandler(ObservationHandler handler) {
            handlers.add(handler);
            return this;
        }

        void publish(CallEvent event) {
            for (ObservationHandler handler : handlers) {
                handler.accept(event);
            }
        }
    }

    // ============================================================
    // 演示入口
    // ============================================================

    public static void main(String[] args) {
        System.out.println("========== 适配器：@Tool 方法 -> LLM 可调用工具 ==========");
        ToolRegistry registry = new ToolRegistry().register(new CalculatorService());
        System.out.println("  注册的工具: " + registry.names());
        System.out.println("  模型调用 calculator_add(3, 4) => " + registry.get("calculator_add").call("3, 4"));
        System.out.println("  模型调用 calculator_multiply(6, 7) => " + registry.get("calculator_multiply").call("6×7"));

        System.out.println();
        System.out.println("========== 策略：多个 ChatClientCustomizer 组合定制 ==========");
        SpringAiCoreSimulation.ChatModel model = new SpringAiCoreSimulation.MockChatModel();
        SpringAiCoreSimulation.ChatClient client = CustomizerApplier.apply(model,
                List.of(new SystemPromptCustomizer("你是严谨的工程师"),
                        new LoggingCustomizer()));
        System.out.println("  => " + client.prompt().user("讲个笑话").call().text());

        System.out.println();
        System.out.println("========== 观察者：调用事件 -> 多个 Handler 订阅 ==========");
        ObservationRegistry registry2 = new ObservationRegistry()
                .addHandler(new PromptContentObservationHandler())
                .addHandler(new CompletionObservationHandler());
        registry2.publish(new CallEvent("STARTED", "今天天气如何？", System.currentTimeMillis()));
        registry2.publish(new CallEvent("COMPLETED", "今天天气如何？", System.currentTimeMillis()));
    }
}
