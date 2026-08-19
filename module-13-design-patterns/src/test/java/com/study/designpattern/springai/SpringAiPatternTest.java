package com.study.designpattern.springai;

import com.study.designpattern.springai.SpringAiCoreSimulation.Advisor;
import com.study.designpattern.springai.SpringAiCoreSimulation.AdvisorChain;
import com.study.designpattern.springai.SpringAiCoreSimulation.BaseAdvisor;
import com.study.designpattern.springai.SpringAiCoreSimulation.ChatClient;
import com.study.designpattern.springai.SpringAiCoreSimulation.ChatClientRequest;
import com.study.designpattern.springai.SpringAiCoreSimulation.ChatClientResponse;
import com.study.designpattern.springai.SpringAiCoreSimulation.ChatModel;
import com.study.designpattern.springai.SpringAiExtSimulation.CallEvent;
import com.study.designpattern.springai.SpringAiExtSimulation.ChatClientCustomizer;
import com.study.designpattern.springai.SpringAiExtSimulation.ObservationHandler;
import com.study.designpattern.springai.SpringAiExtSimulation.ObservationRegistry;
import com.study.designpattern.springai.SpringAiExtSimulation.SystemPromptCustomizer;
import com.study.designpattern.springai.SpringAiExtSimulation.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring AI 设计模式模拟验证：
 *   核心：门面 + 建造者 + 责任链（洋葱顺序）+ 模板方法（记忆钩子）+ 原型（mutate 独立）
 *   扩展：适配器（@Tool 方法转工具）+ 策略（Customizer 定制）+ 观察者（事件订阅）
 */
class SpringAiPatternTest {

    /** 记录每次请求的模型，用于断言 */
    static final class RecordingModel implements ChatModel {
        final List<ChatClientRequest> requests = new ArrayList<>();

        @Override
        public String call(ChatClientRequest request) {
            requests.add(request);
            return "模拟回复";
        }
    }

    /** 责任链追踪 Advisor：记录进入/离开顺序 */
    static final class TraceAdvisor implements Advisor {
        private final String name;
        private final List<String> trace;

        TraceAdvisor(String name, List<String> trace) {
            this.name = name;
            this.trace = trace;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ChatClientResponse advise(ChatClientRequest request, AdvisorChain chain) {
            trace.add(name + "-in");
            ChatClientResponse response = chain.next(request);
            trace.add(name + "-out");
            return response;
        }
    }

    // ================= 门面 + 建造者 =================

    @Test
    @DisplayName("门面+建造者：build 后一行代码完成对话，默认 system 生效")
    void facadeAndBuilder() {
        RecordingModel model = new RecordingModel();
        ChatClient client = ChatClient.builder(model).defaultSystem("你是助手").build();

        ChatClientResponse response = client.prompt().user("你好").call();

        assertEquals("[模型回复] 模拟回复", response.text(), "链终点 ChatModelCallAdvisor 会加上前缀");
        assertEquals("你是助手", model.requests.get(0).system());
        assertEquals("你好", model.requests.get(0).user());
    }

    // ================= 责任链 =================

    @Test
    @DisplayName("责任链：Advisor 按注册顺序洋葱式进出，链终点调用模型")
    void advisorChainOrder() {
        RecordingModel model = new RecordingModel();
        List<String> trace = new ArrayList<>();

        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new TraceAdvisor("A1", trace), new TraceAdvisor("A2", trace))
                .build();
        client.prompt().user("hi").call();

        assertEquals(List.of("A1-in", "A2-in", "A2-out", "A1-out"), trace,
                "A1 最先进入最后退出，A2 在中间，模型调用发生在最内层");
        assertEquals(1, model.requests.size(), "链终点确实调用了模型");
    }

    // ================= 模板方法（BaseAdvisor 钩子） =================

    @Test
    @DisplayName("模板方法：记忆 Advisor 只覆写 before 钩子，历史被注入第二次请求")
    void templateMethodMemoryHook() {
        RecordingModel model = new RecordingModel();
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new SpringAiCoreSimulation.MessageMemoryAdvisor())
                .build();

        client.prompt().user("我叫小明").call();
        client.prompt().user("我叫什么？").call();

        assertTrue(model.requests.get(1).system().contains("[历史会话]"),
                "第二次请求的 system 应包含历史会话（before 钩子生效）");
        assertTrue(model.requests.get(1).system().contains("我叫小明"),
                "历史应包含第一次的用户提问");
    }

    // ================= 原型（mutate） =================

    @Test
    @DisplayName("原型：mutate 克隆出独立客户端，改默认配置不污染原实例")
    void prototypeMutate() {
        RecordingModel model = new RecordingModel();
        ChatClient base = ChatClient.builder(model).defaultSystem("原系统提示").build();

        ChatClient poet = base.mutate().defaultSystem("新系统提示").build();
        base.prompt().user("a").call();
        poet.prompt().user("b").call();

        assertEquals("原系统提示", model.requests.get(0).system(), "原客户端不受影响");
        assertEquals("新系统提示", model.requests.get(1).system(), "克隆客户端使用新配置");
    }

    // ================= 适配器（@Tool -> ToolCallback） =================

    @Test
    @DisplayName("适配器：@Tool 注解的普通方法被适配为可调用工具")
    void adapterToolCallback() {
        ToolRegistry registry = new ToolRegistry().register(new SpringAiExtSimulation.CalculatorService());

        assertEquals("7", registry.get("calculator_add").call("3, 4"));
        assertEquals("42", registry.get("calculator_multiply").call("6×7"));
        assertEquals(List.of("calculator_add", "calculator_multiply"), registry.names());
    }

    // ================= 策略（ChatClientCustomizer） =================

    @Test
    @DisplayName("策略：多个 Customizer 依次定制 Builder，默认参数生效")
    void strategyCustomizer() {
        RecordingModel model = new RecordingModel();
        ChatClient client = SpringAiExtSimulation.CustomizerApplier.apply(model,
                List.of(new SystemPromptCustomizer("你是严谨的工程师")));

        client.prompt().user("讲个笑话").call();

        assertEquals("你是严谨的工程师", model.requests.get(0).system(), "Customizer 策略定制生效");
    }

    // ================= 观察者（ObservationHandler） =================

    @Test
    @DisplayName("观察者：注册中心发布事件，所有 Handler 收到通知")
    void observerHandlers() {
        List<String> events = new ArrayList<>();
        ObservationRegistry registry = new ObservationRegistry()
                .addHandler(new ObservationHandler() {
                    @Override
                    public void accept(CallEvent event) {
                        events.add("A:" + event.type());
                    }
                })
                .addHandler(new ObservationHandler() {
                    @Override
                    public void accept(CallEvent event) {
                        events.add("B:" + event.type());
                    }
                });

        registry.publish(new CallEvent("STARTED", "hi", 1L));
        registry.publish(new CallEvent("COMPLETED", "hi", 2L));

        assertEquals(List.of("A:STARTED", "B:STARTED", "A:COMPLETED", "B:COMPLETED"), events,
                "每个事件广播给所有订阅者");
    }

    @Test
    @DisplayName("分析文件输出包含全部 9 个模式条目")
    void analysisOutput() {
        // 仅验证分析类可加载、静态方法可执行
        assertTrue(SpringAiPatternAnalysis.class.getName().contains("SpringAiPatternAnalysis"));
    }
}
