package com.study.designpattern.springai;

import java.util.ArrayList;
import java.util.List;

/**
 * 【模拟】复刻 spring-ai-client-chat-1.1.8.jar 的核心架构（纯 Java，无任何外部依赖）
 *
 * 覆盖 5 种设计模式，一一对应 jar 中的真实类：
 *
 *   1. 门面 Facade        —— ChatClient / DefaultChatClient
 *   2. 建造者 Builder     —— ChatClient.Builder / DefaultChatClientBuilder
 *   3. 责任链 Chain       —— Advisor / DefaultAdvisorChain / ChatModelCallAdvisor（链终点）
 *   4. 模板方法 Template  —— BaseAdvisor（before/after 钩子）
 *   5. 原型 Prototype     —— ChatClient.mutate() / Builder.clone()
 *
 * 运行：java com.study.designpattern.springai.SpringAiCoreSimulation
 */
public final class SpringAiCoreSimulation {

    // ============================================================
    // 1. 门面 Facade：对外极简，对内封装一切
    // ============================================================

    /** 门面接口：调用方唯一入口（对应 jar 的 ChatClient） */
    public interface ChatClient {
        /** 开始一次会话，返回请求组装器 */
        ChatClientRequestSpec prompt();

        /** 原型模式：基于当前客户端克隆出一个新 Builder，微调后再 build */
        Builder mutate();

        // ---- 静态工厂（简单工厂变体）----
        static Builder builder(ChatModel model) {
            return new DefaultChatClientBuilder(model);
        }

        static ChatClient create(ChatModel model) {
            return builder(model).build();
        }
    }

    /** 门面实现：把 Prompt 组装、Advisor 链、模型调用全部封装（对应 DefaultChatClient） */
    static final class DefaultChatClient implements ChatClient {
        private final ChatClientRequestSpec defaultRequest;

        DefaultChatClient(ChatClientRequestSpec defaultRequest) {
            this.defaultRequest = defaultRequest;
        }

        @Override
        public ChatClientRequestSpec prompt() {
            // 每次会话基于默认请求复制一份，避免请求间互相污染
            return defaultRequest.copy();
        }

        @Override
        public Builder mutate() {
            return defaultRequest.toBuilder();
        }
    }

    // ============================================================
    // 2. 建造者 Builder：链式配置，一次构建处处复用
    // ============================================================

    /** 建造者接口（对应 jar 的 ChatClient.Builder） */
    public interface Builder {
        Builder defaultSystem(String system);

        Builder defaultUser(String user);

        Builder defaultAdvisors(Advisor... advisors);

        /** 原型：克隆当前 Builder（对应 DefaultChatClientBuilder.clone()） */
        Builder clone();

        /** 建造收尾，产出门面实例 */
        ChatClient build();
    }

    /** 建造者实现（对应 DefaultChatClientBuilder） */
    static final class DefaultChatClientBuilder implements Builder {
        private final ChatModel model;
        private final List<Advisor> advisors = new ArrayList<>();
        private String defaultSystem = "";
        private String defaultUser = "";

        DefaultChatClientBuilder(ChatModel model) {
            this.model = model;
        }

        @Override
        public Builder defaultSystem(String system) {
            this.defaultSystem = system;
            return this;
        }

        @Override
        public Builder defaultUser(String user) {
            this.defaultUser = user;
            return this;
        }

        @Override
        public Builder defaultAdvisors(Advisor... advisors) {
            this.advisors.addAll(List.of(advisors));
            return this;
        }

        @Override
        public Builder clone() {
            DefaultChatClientBuilder copy = new DefaultChatClientBuilder(this.model);
            copy.defaultSystem = this.defaultSystem;
            copy.defaultUser = this.defaultUser;
            copy.advisors.addAll(this.advisors);
            return copy;
        }

        @Override
        public ChatClient build() {
            ChatClientRequestSpec spec = new ChatClientRequestSpec(model);
            spec.system(defaultSystem);
            spec.user(defaultUser);
            spec.advisors(advisors);
            return new DefaultChatClient(spec);
        }
    }

    // ============================================================
    // 3. 责任链 Chain of Responsibility：Advisor 逐级传递
    // ============================================================

    /** 责任链接口（对应 jar 的 CallAdvisor） */
    public interface Advisor {
        String getName();

        ChatClientResponse advise(ChatClientRequest request, AdvisorChain chain);
    }

    /** 链对象：负责把请求交给下一个 Advisor（对应 DefaultAroundAdvisorChain） */
    public interface AdvisorChain {
        ChatClientResponse next(ChatClientRequest request);
    }

    /** 责任链核心：按顺序推进，最后一个 Advisor 之后落到链终点（模型调用） */
    static final class DefaultAdvisorChain implements AdvisorChain {
        private final List<Advisor> advisors;
        private final int index;
        private final ChatModelCallAdvisor terminal;

        DefaultAdvisorChain(List<Advisor> advisors, int index, ChatModelCallAdvisor terminal) {
            this.advisors = advisors;
            this.index = index;
            this.terminal = terminal;
        }

        @Override
        public ChatClientResponse next(ChatClientRequest request) {
            if (index < advisors.size()) {
                Advisor advisor = advisors.get(index);
                // 交给链上第 index 个 Advisor，并传入"指向下一个"的新链
                return advisor.advise(request, new DefaultAdvisorChain(advisors, index + 1, terminal));
            }
            // 链的终点：真正调用模型（对应 jar 的 ChatModelCallAdvisor）
            return terminal.adviseCall(request);
        }
    }

    /** 链终点：调用底层模型（对应 jar 的 ChatModelCallAdvisor，order 最低、最后一个执行） */
    static final class ChatModelCallAdvisor {
        private final ChatModel model;

        ChatModelCallAdvisor(ChatModel model) {
            this.model = model;
        }

        ChatClientResponse adviseCall(ChatClientRequest request) {
            String text = model.call(request);
            return new ChatClientResponse("[模型回复] " + text);
        }
    }

    // ============================================================
    // 4. 模板方法 Template Method：骨架 + 钩子
    // ============================================================

    /**
     * 模板方法基类（对应 jar 的 BaseAdvisor）：
     * advise() 是骨架 —— 先 before() 预处理请求，沿链继续，再 after() 处理响应；
     * 子类只需覆写 before/after 两个钩子，链的推进与调度由基类统一保证。
     */
    public abstract static class BaseAdvisor implements Advisor {
        @Override
        public ChatClientResponse advise(ChatClientRequest request, AdvisorChain chain) {
            ChatClientRequest prepared = before(request);          // 钩子 1：请求预处理
            ChatClientResponse response = chain.next(prepared);    // 骨架：沿链继续
            return after(response);                                // 钩子 2：响应后处理
        }

        /** 钩子：默认原样返回，子类可覆写 */
        protected ChatClientRequest before(ChatClientRequest request) {
            return request;
        }

        /** 钩子：默认原样返回，子类可覆写 */
        protected ChatClientResponse after(ChatClientResponse response) {
            return response;
        }
    }

    /** 具体职责 1：日志 Advisor（记录请求与响应） */
    static final class LoggerAdvisor extends BaseAdvisor {
        @Override
        public String getName() {
            return "LoggerAdvisor";
        }

        @Override
        protected ChatClientResponse after(ChatClientResponse response) {
            System.out.println("    [LoggerAdvisor] 调用完成，耗时 120ms");
            return response;
        }
    }

    /** 具体职责 2：记忆 Advisor（把历史会话注入请求的 system 部分） */
    static final class MessageMemoryAdvisor extends BaseAdvisor {
        private final List<String> history = new ArrayList<>();

        @Override
        public String getName() {
            return "MessageMemoryAdvisor";
        }

        @Override
        protected ChatClientRequest before(ChatClientRequest request) {
            String merged = history.isEmpty() ? request.system()
                    : request.system() + "\n[历史会话]\n" + String.join("\n", history);
            history.add("用户: " + request.user());
            return request.withSystem(merged);
        }
    }

    // ============================================================
    // 请求 / 响应 / 模型（最小模型层）
    // ============================================================

    /** 请求组装器（对应 jar 的 ChatClientRequestSpec）：门面的"会话内"入口 */
    public static final class ChatClientRequestSpec {
        private final ChatModel model;
        private String system = "";
        private String user = "";
        private List<Advisor> advisors = List.of();

        ChatClientRequestSpec(ChatModel model) {
            this.model = model;
        }

        public ChatClientRequestSpec system(String system) {
            this.system = system;
            return this;
        }

        public ChatClientRequestSpec user(String user) {
            this.user = user;
            return this;
        }

        ChatClientRequestSpec advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        /** 执行调用：组装请求 -> 进入责任链 */
        public ChatClientResponse call() {
            ChatClientRequest request = new ChatClientRequest(system, user);
            AdvisorChain chain = new DefaultAdvisorChain(advisors, 0, new ChatModelCallAdvisor(model));
            System.out.println("  >> 请求进入责任链，Advisor: " + advisors.stream().map(Advisor::getName).toList());
            return chain.next(request);
        }

        ChatClientRequestSpec copy() {
            ChatClientRequestSpec copy = new ChatClientRequestSpec(model);
            copy.system = this.system;
            copy.user = this.user;
            copy.advisors = this.advisors;
            return copy;
        }

        Builder toBuilder() {
            DefaultChatClientBuilder b = new DefaultChatClientBuilder(model);
            b.defaultSystem(this.system);
            b.defaultUser(this.user);
            b.defaultAdvisors(this.advisors.toArray(new Advisor[0]));
            return b;
        }
    }

    /** 请求对象（对应 jar 的 ChatClientRequest） */
    public record ChatClientRequest(String system, String user) {
        ChatClientRequest withSystem(String newSystem) {
            return new ChatClientRequest(newSystem, user);
        }
    }

    /** 响应对象（对应 jar 的 ChatClientResponse） */
    public record ChatClientResponse(String text) {
    }

    /** 最小模型（对应 jar 的 ChatModel） */
    public interface ChatModel {
        String call(ChatClientRequest request);
    }

    /** 模拟模型实现 */
    static final class MockChatModel implements ChatModel {
        @Override
        public String call(ChatClientRequest request) {
            System.out.println("    [ChatModel] 收到请求，system 前 20 字: "
                    + request.system().substring(0, Math.min(20, request.system().length())) + "...");
            return "你好，我是模拟 AI！";
        }
    }

    // ============================================================
    // 演示入口
    // ============================================================

    public static void main(String[] args) {
        ChatModel model = new MockChatModel();

        System.out.println("========== 门面 + 建造者：一次构建，处处复用 ==========");
        ChatClient client = ChatClient.builder(model)
                .defaultSystem("你是一个乐于助人的助手")
                .defaultAdvisors(new LoggerAdvisor(), new MessageMemoryAdvisor())
                .build();

        System.out.println("会话 1：");
        ChatClientResponse r1 = client.prompt().user("今天天气如何？").call();
        System.out.println("  => " + r1.text());

        System.out.println("会话 2（记忆生效，历史被注入）：");
        ChatClientResponse r2 = client.prompt().user("刚才我问了什么？").call();
        System.out.println("  => " + r2.text());

        System.out.println();
        System.out.println("========== 原型 mutate()：基于现有客户端克隆定制 ==========");
        ChatClient poet = client.mutate()
                .defaultSystem("你是一位诗人，用诗句回答")
                .build();
        ChatClientResponse r3 = poet.prompt().user("介绍一下你自己").call();
        System.out.println("  => " + r3.text());
        System.out.println("  原 client 不受影响（原型克隆互不污染），再问一次原 client：");
        System.out.println("  => " + client.prompt().user("再问一次").call().text());
    }
}
