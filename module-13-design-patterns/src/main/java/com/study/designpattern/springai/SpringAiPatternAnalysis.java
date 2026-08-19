package com.study.designpattern.springai;

/**
 * 【框架源码分析】spring-ai-client-chat-1.1.8.jar 中的设计模式全景
 *
 * 分析对象：~/.m2/repository/org/springframework/ai/spring-ai-client-chat/1.1.8/spring-ai-client-chat-1.1.8.jar
 * 分析方法：解压 jar + javap 反编译核心类的 public API，逐一核对每个模式对应的类与方法。
 *
 * 识别结果（8 种设计模式 + 1 种工厂变体）：
 *
 * ┌───────────────┬──────────────────────────────────────────────────────┬──────────────────────────────┐
 * │ 设计模式       │ jar 中的落点（类 / 方法）                              │ 学习要点                      │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 门面 Facade    │ ChatClient 接口 + DefaultChatClient                  │ 对外 3 个方法，内部封装        │
 * │               │ prompt() / mutate() / builder()                      │ Prompt 组装+Advisor链+模型调用 │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 建造者 Builder │ ChatClient.Builder + DefaultChatClientBuilder        │ defaultSystem/defaultUser/    │
 * │               │ + 各 Advisor 的 $Builder（如 MessageChatMemoryAdvisor │ defaultAdvisors/build() 链式  │
 * │               │ .builder()）+ ChatClientRequest$Builder 等            │ 配置；一次构建处处复用        │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 责任链 Chain   │ advisor.api.CallAdvisor/StreamAdvisor +             │ adviseCall(request, chain)    │
 * │               │ DefaultAroundAdvisorChain.nextCall()/nextStream()    │ 逐级传递，ChatModelCallAdvisor │
 * │               │ + ChatModelCallAdvisor（链终点）                      │ 是链终点真正调模型            │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 模板方法       │ advisor.api.BaseAdvisor（default adviseCall/         │ 骨架：调度+before/after 钩子， │
 * │ Template      │ adviseStream）+ BaseChatMemoryAdvisor                │ 子类只实现 before()/after()   │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 原型 Prototype │ ChatClient.Builder.clone() + ChatClient.mutate()    │ 基于现有配置克隆新 Builder，  │
 * │               │ + DefaultChatClientBuilder.clone()                   │ 微调后 build 出独立实例       │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 适配器 Adapter │ Builder.defaultTools(Object...) → ToolCallbackUtils  │ 把 @Tool POJO 方法适配成      │
 * │               │ → ToolCallback                                       │ 模型可调用的工具回调          │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 策略 Strategy  │ ChatClientCustomizer.customize(Builder)（Spring Boot │ 每个 Customizer 是一种定制    │
 * │               │ 自动配置收集所有 Customizer Bean 依次应用）            │ 策略，可插拔组合              │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 观察者 Observer│ ChatClientCompletionObservationHandler +             │ Micrometer Observation 订阅   │
 * │               │ ChatClientPromptContentObservationHandler            │ 调用事件做埋点/日志           │
 * ├───────────────┼──────────────────────────────────────────────────────┼──────────────────────────────┤
 * │ 静态工厂       │ ChatClient.create(model) / builder(model)            │ 简单工厂变体，隐藏实现类      │
 * └───────────────┴──────────────────────────────────────────────────────┴──────────────────────────────┘
 *
 * javap 证据摘录（关键 API）：
 *
 *   public interface ChatClient {
 *     static ChatClient create(ChatModel);                       // 静态工厂
 *     static ChatClient$Builder builder(ChatModel);              // 工厂 -> 建造者
 *     ChatClientRequestSpec prompt();                            // 门面：极简入口
 *     ChatClient$Builder mutate();                               // 原型：克隆 Builder
 *   }
 *   public interface ChatClient$Builder {
 *     Builder defaultAdvisors(Advisor...);
 *     Builder defaultOptions(ChatOptions);
 *     Builder defaultSystem(String);
 *     Builder defaultUser(String);
 *     Builder clone();                                           // 原型
 *     ChatClient build();                                        // 建造者收尾
 *   }
 *   public interface CallAdvisor extends Advisor {
 *     ChatClientResponse adviseCall(ChatClientRequest, CallAdvisorChain);   // 责任链传递
 *   }
 *   public interface BaseAdvisor extends CallAdvisor, StreamAdvisor {
 *     default ChatClientResponse adviseCall(...) { ... }         // 模板方法骨架
 *     abstract ChatClientRequest before(ChatClientRequest, AdvisorChain);   // 钩子
 *     abstract ChatClientResponse after(ChatClientResponse, AdvisorChain);  // 钩子
 *   }
 *   public class DefaultAroundAdvisorChain implements BaseAdvisorChain {
 *     ChatClientResponse nextCall(ChatClientRequest);            // 链式转发
 *   }
 *   public interface ChatClientCustomizer {
 *     void customize(ChatClient$Builder);                        // 策略
 *   }
 *
 * 配套模拟示例（纯 Java 复刻，无需 Spring AI 依赖）：
 *   SpringAiCoreSimulation —— 门面/建造者/责任链/模板方法/原型
 *   SpringAiExtSimulation  —— 适配器/策略/观察者
 */
public final class SpringAiPatternAnalysis {

    private SpringAiPatternAnalysis() {
    }

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("  spring-ai-client-chat-1.1.8.jar 设计模式分析");
        System.out.println("============================================================");
        System.out.println();
        print("门面 Facade", "ChatClient / DefaultChatClient",
                "对外只暴露 prompt()、mutate()、静态 builder()/create() 三个入口，"
                        + "内部把 Prompt 组装、Advisor 责任链、ChatModel 调用、Observation 埋点全部封装起来。"
                        + "调用方一行代码完成一次对话，复杂度全部收敛在门面之后。");
        print("建造者 Builder", "ChatClient.Builder / DefaultChatClientBuilder",
                "builder(ChatModel) 返回 Builder，defaultSystem/defaultUser/defaultAdvisors/defaultTools 链式配置，"
                        + "build() 产出 ChatClient。一次配置、多处复用；每个 Advisor 也有自己的 $Builder"
                        + "（MessageChatMemoryAdvisor.builder(...) 等），构造细节从调用方隔离。");
        print("责任链 Chain of Responsibility", "CallAdvisor/StreamAdvisor + DefaultAroundAdvisorChain",
                "每个 Advisor 实现 adviseCall(request, chain)，处理完自己的逻辑后决定是否 chain.next() 交给下一个；"
                        + "DefaultAroundAdvisorChain.nextCall() 负责依次推进，ChatModelCallAdvisor 是链的终点，"
                        + "真正调用底层模型。记忆、日志、安全过滤、工具调用都是这条链上的一环，可任意增删排序。");
        print("模板方法 Template Method", "BaseAdvisor / BaseChatMemoryAdvisor",
                "BaseAdvisor 的 adviseCall/adviseStream 是 default 骨架：先 before() 预处理请求，再沿链继续，"
                        + "最后 after() 处理响应。子类（如 MessageChatMemoryAdvisor）只覆写 before/after 两个钩子，"
                        + "骨架逻辑（调度、链推进、异常处理）由基类统一保证。");
        print("原型 Prototype", "ChatClient.Builder.clone() + ChatClient.mutate()",
                "mutate() 返回基于当前客户端配置克隆出的新 Builder，调用方可以在不污染原实例的前提下"
                        + "微调（换个 system prompt、加个 advisor），再 build() 出一个独立新客户端。"
                        + "典型场景：全局 ChatClient + 按请求定制。");
        print("适配器 Adapter", "Builder.defaultTools(Object...) -> ToolCallback",
                "把带有 @Tool 注解的普通 POJO 方法，通过 ToolCallbackUtils 适配成模型可调用的 ToolCallback"
                        + "（名称/描述/参数 Schema/执行器），让 LLM 能「看见」并「调用」任意 Java 方法，"
                        + "无需改动被适配对象本身。");
        print("策略 Strategy", "ChatClientCustomizer",
                "Spring Boot 自动配置收集容器里所有 ChatClientCustomizer Bean，依次 customize(builder) 做定制；"
                        + "每个 Customizer 是一种可插拔策略（加日志、加记忆、设默认参数……），组合自由、互不侵入。");
        print("观察者 Observer", "ChatClientCompletionObservationHandler / ChatClientPromptContentObservationHandler",
                "Micrometer Observation 体系：调用开始/完成时发布事件，Handler 订阅事件做埋点、日志、指标。"
                        + "业务代码零侵入，观察者随时可增可减。");
        print("静态工厂 Simple Factory", "ChatClient.create(model) / ChatClient.builder(model)",
                "静态工厂方法隐藏 DefaultChatClient 实现类，并顺带解决「接口 + 默认实现」的装配问题。");
        System.out.println("------------------------------------------------------------");
        System.out.println("运行配套模拟：SpringAiCoreSimulation（核心 5 模式）");
        System.out.println("             SpringAiExtSimulation（扩展 3 模式）");
    }

    private static void print(String pattern, String location, String point) {
        System.out.println("■ " + pattern);
        System.out.println("  落点：" + location);
        System.out.println("  要点：" + point);
        System.out.println();
    }
}
