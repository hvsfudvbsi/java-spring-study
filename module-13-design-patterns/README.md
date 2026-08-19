# module-13-design-patterns · 设计模式

> 纯 Java 模块（不依赖 Spring）。覆盖 GoF 23 种经典设计模式，每个模式均给出**常用写法 + 不常用写法**，最后附 3 个组合多个模式的完整实操示例。
> Java 21。

## 📖 本模块内容

### 第一部分：创建型模式（5 种）

| 类 | 模式 | 常用写法 | 不常用写法 |
|----|------|---------|-----------|
| `creational/SingletonDemo` | 单例 | 饿汉式、懒汉式 DCL（双重检查锁 + volatile）、静态内部类 | 枚举单例（最安全）、防反射/防序列化加固、反射攻击演示 |
| `creational/FactoryMethodDemo` | 工厂方法 | 抽象工厂接口 + 每种产品一个具体工厂 | Supplier 注册表工厂（动态注册）、反射工厂（Class.forName） |
| `creational/AbstractFactoryDemo` | 抽象工厂 | 产品族（按钮/输入框）x 主题工厂（浅色/深色）成套创建 | 函数式注册表按主题取工厂、与工厂方法对比 |
| `creational/BuilderDemo` | 建造者 | 链式 Builder + build() 统一校验，产出不可变对象 | record + Builder、record withXxx 拷贝修改、JDK 内置（StringBuilder/Stream.builder） |
| `creational/PrototypeDemo` | 原型 | Cloneable + clone() 浅拷贝 | 深拷贝（集合元素逐一克隆）、原型注册表 Map、record 替代 |

### 第二部分：结构型模式（7 种）

| 类 | 模式 | 常用写法 | 不常用写法 |
|----|------|---------|-----------|
| `structural/AdapterDemo` | 适配器 | 对象适配器（组合），国标插座 -> 欧标插头 | JDK 内置适配器（Arrays.asList / InputStreamReader / Collections.enumeration）、方法引用适配 |
| `structural/BridgeDemo` | 桥接 | 形状（抽象）x 渲染器（实现）组合，避免类爆炸 | 函数式渲染器直接注入、JDBC DriverManager/Driver 是桥接 |
| `structural/CompositeDemo` | 组合 | 文件系统树：叶子（文件）+ 容器（目录）统一接口、递归求和 | Stream 递归拍平（flatten）、透明组合 vs 安全组合 |
| `structural/DecoratorDemo` | 装饰器 | 咖啡加奶/糖/奶油层层叠加，运行期任意组合 | JDK 内置（BufferedInputStream / synchronizedList）、Function 组合计时装饰 |
| `structural/FacadeDemo` | 外观 | 一键下单：封装库存/支付/物流三个子系统 | 抽象门面接口、外观 vs 中介者对比 |
| `structural/FlyweightDemo` | 享元 | 棋子享元：内部状态（颜色）共享 + 外部状态（坐标）传入 | Integer.valueOf 缓存、String 常量池、线程池/连接池也是享元 |
| `structural/ProxyDemo` | 代理 | 静态代理（日志） | JDK 动态代理（InvocationHandler）、虚拟代理（延迟加载）、保护代理（权限） |

### 第三部分：行为型模式（11 种）

| 类 | 模式 | 常用写法 | 不常用写法 |
|----|------|---------|-----------|
| `behavioral/ChainOfResponsibilityDemo` | 责任链 | 审批链按金额分级（组长->经理->总监->CEO），中断式 | 函数式责任链（List&lt;Function&gt;）、中断式 vs 全链式 |
| `behavioral/CommandDemo` | 命令 | 遥控器 + 撤销栈（开灯/关灯命令） | 宏命令（一键回家模式批量撤销）、Runnable 函数式命令 |
| `behavioral/IteratorDemo` | 迭代器 | 自定义 BookShelf 实现 Iterable + for-each | 自定义倒序迭代器、Iterator 转 Stream、fail-fast 演示 |
| `behavioral/MediatorDemo` | 中介者 | 聊天室：用户只认识中介者，不直接互相引用 | EventBus 风格函数式中介者（按事件类型注册消费者） |
| `behavioral/MementoDemo` | 备忘录 | 编辑器快照 + 历史栈撤销（record 天然不可变） | 带版本号快照、与命令模式组合实现多级撤销 |
| `behavioral/ObserverDemo` | 观察者 | 气象站推模型：注册/取消注册/通知 | 函数式订阅（Consumer 回调）、JDK Observable 已废弃说明 |
| `behavioral/StateDemo` | 状态 | 状态类 + 上下文委托，订单状态机 | 枚举 + 转移表（生产最实用）、与策略模式区别 |
| `behavioral/StrategyDemo` | 策略 | 支付策略运行期切换 | 函数式策略（lambda 即策略）、Comparator 策略、枚举策略 |
| `behavioral/TemplateMethodDemo` | 模板方法 | 饮品制作骨架固定 + 抽象步骤 + 钩子方法 | 函数式模板（步骤作为 Supplier 传入）、JdbcTemplate 说明 |
| `behavioral/VisitorDemo` | 访问者 | 双分派：Shape.accept -> AreaVisitor/InfoVisitor | 函数式 Map&lt;Class, Function&gt; 分派、与 instanceof 对比 |
| `behavioral/InterpreterDemo` | 解释器 | 表达式树手工组装（终结符/非终结符） | 递归下降解析器（乘除优先、从左到右）、Pattern/SpEL 说明 |

### 第四部分：完整实操示例（组合多个模式）

| 实操 | 文件 | 场景 | 组合的模式 |
|------|------|------|-----------|
| 在线商城下单 | `practice/OnlineOrderSystemDemo` | 下单 -> 支付 -> 发货，普通/秒杀/团购订单 | 工厂方法（订单类型+折扣）、策略（支付方式）、观察者（短信/邮件通知）、状态（枚举+转移表）、模板方法（流程骨架+秒杀限购钩子） |
| 审批流引擎 | `practice/ApprovalWorkflowDemo` | 报销单按金额多级审批，提交前快照可回退 | 责任链（分级审批）、状态（草稿->审批中->通过/驳回）、备忘录（快照+历史栈回退） |
| 文档导出中心 | `practice/DocumentExportDemo` | 内部/遗留数据统一导出，可叠加水印/压缩/加密 | 适配器（遗留系统接口）、享元（字体复用）、装饰器（加密->压缩->水印）、外观（一键导出） |

### 第五部分：框架源码分析（spring-ai-client-chat 1.1.8 中的设计模式）

> 分析对象：`spring-ai-client-chat-1.1.8.jar`（Maven 本地仓库 ~/.m2 下），方法：解压 jar + javap 反编译核心类，逐模式核对类与方法。
> 配套**纯 Java 模拟**（无需 Spring AI 依赖），复刻 jar 的架构设计，可直接运行学习。

| 设计模式 | jar 中的落点 | 学习要点 | 模拟示例 |
|---------|-------------|---------|---------|
| 门面 Facade | `ChatClient` / `DefaultChatClient` | 对外只暴露 `prompt()`/`mutate()`/`builder()` 几个入口，内部封装 Prompt 组装、Advisor 链、模型调用 | `SpringAiCoreSimulation` |
| 建造者 Builder | `ChatClient.Builder` / `DefaultChatClientBuilder`（+ 各 Advisor 的 `$Builder`） | `defaultSystem/defaultUser/defaultAdvisors/build()` 链式配置，一次构建处处复用 | `SpringAiCoreSimulation` |
| 责任链 Chain | `advisor.api.CallAdvisor/StreamAdvisor` + `DefaultAroundAdvisorChain` + `ChatModelCallAdvisor`（链终点） | `adviseCall(request, chain)` 逐级传递；记忆/日志/安全/工具都是链上一环 | `SpringAiCoreSimulation` |
| 模板方法 Template | `advisor.api.BaseAdvisor`（default 骨架）+ `BaseChatMemoryAdvisor` | 骨架负责调度与链推进，子类只覆写 `before()`/`after()` 钩子 | `SpringAiCoreSimulation` |
| 原型 Prototype | `ChatClient.Builder.clone()` + `ChatClient.mutate()` | 基于现有配置克隆新 Builder，微调后 build 出独立实例，互不污染 | `SpringAiCoreSimulation` |
| 适配器 Adapter | `Builder.defaultTools(Object...)` -> `ToolCallbackUtils` -> `ToolCallback` | 把 `@Tool` 注解的普通 Java 方法适配成 LLM 可调用工具 | `SpringAiExtSimulation` |
| 策略 Strategy | `ChatClientCustomizer.customize(Builder)` | Spring Boot 收集所有 Customizer Bean 依次应用，可插拔组合 | `SpringAiExtSimulation` |
| 观察者 Observer | `ChatClientCompletionObservationHandler` / `ChatClientPromptContentObservationHandler` | Micrometer Observation 发布事件，Handler 订阅埋点，业务零侵入 | `SpringAiExtSimulation` |
| 静态工厂（变体） | `ChatClient.create(model)` / `builder(model)` | 简单工厂隐藏实现类，顺带解决"接口 + 默认实现"装配 | `SpringAiCoreSimulation` |

**文件说明**：
- `springai/SpringAiPatternAnalysis` —— 分析结果（模式 -> 落点 -> javap 证据 -> 要点），可直接运行查看
- `springai/SpringAiCoreSimulation` —— 核心 5 模式模拟（门面/建造者/责任链/模板方法/原型）
- `springai/SpringAiExtSimulation` —— 扩展 3 模式模拟（适配器/策略/观察者）
- 测试 `springai/SpringAiPatternTest` —— 8 个用例验证各模式行为（链顺序/记忆钩子/原型独立/工具调用/策略定制/事件广播）

## 🚀 运行方式

```bash
# 测试（覆盖所有模式与实操验证）
mvn test -pl module-13-design-patterns

# 运行全部 23 个模式用例
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.Main

# 实操一：在线商城下单系统
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.practice.OnlineOrderSystemDemo

# 实操二：审批流引擎
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.practice.ApprovalWorkflowDemo

# 实操三：文档导出中心
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.practice.DocumentExportDemo

# 第五部分一：Spring AI 设计模式分析
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.springai.SpringAiPatternAnalysis

# 第五部分二：核心 5 模式模拟（门面/建造者/责任链/模板方法/原型）
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.springai.SpringAiCoreSimulation

# 第五部分三：扩展 3 模式模拟（适配器/策略/观察者）
mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.springai.SpringAiExtSimulation
```

## 🔍 核心概念讲解（面试必问）

### 1. 六大设计原则（SOLID + 最少知识）
| 原则 | 一句话 | 本模块对应 |
|------|--------|-----------|
| 单一职责 SRP | 一个类只干一件事 | 各模式的职责拆分 |
| 开闭原则 OCP | 对扩展开放、对修改关闭 | 装饰器/策略/观察者 |
| 里氏替换 LSP | 子类能替换父类且行为正确 | 模板方法/工厂方法 |
| 接口隔离 ISP | 不强迫依赖不需要的接口 | 适配器/门面 |
| 依赖倒置 DIP | 依赖抽象，不依赖具体 | 桥接/策略/抽象工厂 |
| 最少知识（迪米特） | 只跟朋友说话 | 外观/中介者 |

### 2. 创建型三问
- **单例 vs 静态类**：单例可继承、可延迟初始化、可被 Spring 管理；静态类纯工具。
- **工厂方法 vs 抽象工厂**：一个工厂产一种产品 vs 一个工厂产一族产品。
- **建造者 vs 构造器**：参数多且可选 -> 建造者；参数少且必填 -> 构造器。

### 3. 结构型对比表
| 模式 | 作用 | 一句话记忆 |
|------|------|-----------|
| 适配器 | 接口不兼容 | 转接头 |
| 桥接 | 抽象与实现分离 | 形状 x 颜色自由组合 |
| 组合 | 树形结构 | 文件系统 |
| 装饰器 | 动态叠加功能 | 咖啡加糖加奶 |
| 外观 | 统一入口 | 前台接待 |
| 享元 | 共享复用 | 黑白棋子各一个 |
| 代理 | 控制访问 | 中介/替身 |

### 4. 行为型高频对比
- **策略 vs 状态**：策略是"换算法"（客户端选择，无关联）；状态是"自动流转"（状态之间有关联转移）。
- **观察者 vs 中介者 vs 外观**：观察者一对多广播；中介者多对多收敛为多对一；外观是单向门面。
- **模板方法 vs 策略**：模板方法用继承固定骨架；策略用组合替换算法。
- **命令 vs 备忘录**：命令管"操作与撤销"；备忘录管"状态快照与恢复"；两者常组合实现多级撤销。

### 5. 双分派与访问者
```
shape.accept(visitor)  ->  visitor.visit(circle)     // 由元素的运行时类型决定调用哪个 visit
```
元素调用 accept（第一次分派），accept 里 `visitor.visit(this)`（第二次分派）——两次分派合起来选中最具体的 visit 重载。

### 6. 状态机两种实现
| 方式 | 优点 | 缺点 |
|------|------|------|
| 状态类（State 接口 + 具体状态） | 每个状态的行为内聚、可加字段 | 状态多时类爆炸 |
| 枚举 + 转移表 | 一个表看清所有流转、校验集中 | 行为逻辑需配合 switch/表驱动 |

## ✍️ 动手练习

1. 给 `DecoratorDemo` 新增一个"加珍珠"装饰器，并组合出"美式+奶+糖+珍珠"，验证价格正确叠加。
2. 给 `ChainOfResponsibilityDemo` 增加"全链式"过滤器链（登录校验 -> 参数校验 -> 业务处理），理解中断式与全链式的区别。
3. 把 `OnlineOrderSystemDemo` 的秒杀流程改成：先校验再通知、通知顺序变为"邮件 -> 短信"，体会模板方法中"钩子"的作用。
4. 给 `ApprovalWorkflowDemo` 增加"驳回 -> 重新提交"的状态流转，思考驳回后责任链如何重新走。
5. 用 JDK 动态代理给 `VisitorDemo` 的访问者加耗时统计（提示：Proxy.newProxyInstance 包一层），对比手写计时。
6. 给 `InterpreterDemo` 增加除法和括号支持（提示：递归下降里加一层 paren 解析），验证 `(1+2)*3 = 9`。
7. 把 `ObserverDemo` 改成拉模型：观察者收到通知后主动调用 `station.temperature()/humidity()` 取值。
8. ✅ 已实现：`StateDemo` 提供"状态类"与"枚举+转移表"两套写法，对照阅读理解两种实现各自的适用场景。
9. ✅ 已实现：`springai/SpringAiPatternAnalysis` 逐模式分析了 spring-ai-client-chat-1.1.8.jar 的源码设计（门面/建造者/责任链/模板方法/原型/适配器/策略/观察者/静态工厂），并附纯 Java 模拟与测试——学完 23 个模式后，对照真实框架源码看它们如何被组合使用。
