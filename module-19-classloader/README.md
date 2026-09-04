# module-19-classloader — 类加载机制

> 纯 Java 模块（不依赖 Spring），通过可运行的 Demo + 59 个测试，从原理到实战掌握
> **类加载机制、双亲委派、类冲突隔离、类加载/卸载**——并完整回答面试题
> 「两个 jar 存在相同类名但都要用怎么办」「两个模块不同时使用能否靠类的加载/卸载机制切换」。

## 一、目录结构与知识点映射

| 包 | 类 | 覆盖知识点 |
|---|---|---|
| `basic` | `ClassLoadingLifecycleDemo` / `SampleClass` / `InitRecorder` | 加载→验证→准备→解析→初始化 五阶段；哪些操作触发初始化（new / forName / getstatic / 静态方法），哪些不触发（loadClass / forName(false) / 编译期常量 ldc 内联）；`<clinit>` 只执行一次；父类先于子类初始化 |
| `delegation` | `DelegationChainDemo` | JDK 9+ 三层类加载器：Bootstrap(null) ← Platform ← Application；双亲委派流程（findLoadedClass → 父 → 自己）；为什么必须委派（安全、一致性、缓存复用） |
| `delegation.custom` | `FileClassLoader` / `TargetClass` | 自定义类加载器：只重写 `findClass` + `defineClass` 即遵循双亲委派；从目录（模拟 jar）读字节码加载类；多目录顺序搜索；findLoadedClass 缓存 |
| `delegation.custom` | `JarClassLoader` / `JarClassLoaderDemo` | 同上，但输入是**真实 .jar 文件**：用 `JarFile` 按 entry 名读 `.class` 字节码再 `defineClass`（README 动手练习 1 的落地）；多 jar 顺序搜索、同名类先声明先生效、jar 与目录加载出不同类身份 |
| `delegation.custom` | `BreakDelegationClassLoader` | 打破双亲委派（父最后 / parent-last）：重写 `loadClass` 先自己加载；只对指定命名空间生效，`java.*` 仍委派 Bootstrap；类身份 = 全限定名 + 定义加载器 |
| `delegation` | `DelegationBreakDemo` | 委派 vs 打破委派对照实验：同名类两种策略加载出不同 Class，跨加载器只能反射/接口交互 |
| `spi` | `Greeting` / `TccLDemo` | SPI 与线程上下文类加载器（TCCL）：框架代码如何加载"看不见"的第三方实现；JDBC DriverManager 同款机制；用后必须恢复 TCCL |
| `util` | `RuntimeCompiler` | 用 `javax.tools.JavaCompiler` 在运行时把源码字符串编译成 `.class` 目录（等价于"一个 jar 解压目录"），是本模块所有"两个 jar"场景的原料；`packageToJar` 再把目录打成真实 `.jar` |
| `conflict` | `IGreeter` / `ClassConflictDemo` | 类冲突（Jar Hell）：同一个类加载器里同名类只有一份，谁先加载谁生效（classpath 顺序 / Maven 仲裁）；先加载版本实现共同接口可直接调用 |
| `conflict` | `IsolationDemo` | 隔离加载：两个 jar 同名类【都要用】→ 每个 jar 一个专属类加载器 + 父加载器提供的共同接口，两个版本共存且类型安全 |
| `unload` | `ClassUnloadDemo` | 类卸载三条件（Class 无强引用 + 加载器无强引用 + 该加载器全部类无引用）；WeakReference + System.gc() 验证；卸载后可重新加载 |
| `plugin` | `PluginVersion` / `PluginRunner` | 插件系统实战：两个同名"服务模块"不同时使用 → 独立加载器 + 用完卸载，热切换 v1/v2/v1 互不残留（Tomcat reload / OSGi 同款原理） |
| `Main` | 入口 | 依次演示全部 8 个知识点 |

## 二、运行方式

```bash
# 运行全部 Demo（9 个小节）
mvn compile exec:java -pl module-19-classloader -Dexec.mainClass=com.study.classloader.Main
# 或编译后直接跑
cd module-19-classloader && java -cp target/classes com.study.classloader.Main

# 运行测试（59 个）
mvn test -pl module-19-classloader
```

## 三、核心概念与原理

### 1. 类加载的五个阶段（JVM 规范第 5 章）

```
.class 字节码 ──加载──> 内存中的 Class 对象
              ──验证──> 字节码安全校验
              ──准备──> 静态字段分配内存 + 默认值（0/null/false）
              ──解析──> 常量池符号引用 -> 直接引用
              ──初始化──> 执行 <clinit>（静态赋值 + 静态代码块）
```

只有"初始化"执行用户写的静态代码，所以能直接观察。**触发初始化**：`new`、反射
`Class.forName(name)`（默认 initialize=true）、访问非常量静态字段（getstatic）、调用
静态方法、初始化子类前先初始化父类。**不触发**：`ClassLoader.loadClass()`、
`Class.forName(name, false, loader)`、访问编译期常量（javac 内联成 ldc，字节码里
根本没有对类的引用）、通过数组引用类。`<clinit>` 每个类在 JVM 里**只执行一次**。

> 测试怎么保证确定性：普通类初始化一次后状态就固定了，所以测试用 `RuntimeCompiler`
> 每次运行时生成新的探针类 + 全新的类加载器，让每个场景都从"未初始化"开始。

### 2. 双亲委派模型（JDK 9+）

```
请求加载 A 类
  └─> ① findLoadedClass(A) 已加载？直接返回
      └─> ② 父加载器存在 → 父.loadClass(A)（递归向上，直到 Bootstrap）
          └─> ③ 父都抛 ClassNotFoundException → 自己 findClass(A) + defineClass
```

三个加载器层次：**Bootstrap**（C++ 实现，Java 侧为 null，加载 `java.*`）、**Platform**
（JDK 9 起取代 Extension，加载 `java.sql`、`javax.sql` 等平台模块）、**Application**
（加载 classpath 下用户类）、再往下是自定义类加载器。

为什么必须双亲委派：① **安全**——用户自定义 `java.lang.String` 的加载请求会被委派到
Bootstrap，你写的同名类根本没机会加载，核心类不可被篡改；② **一致性**——同一个类
在 JVM 里只有一份 Class 对象，不会出现两个互相不认识的 String；③ **缓存复用**——
父已加载的类子加载器直接复用。

### 3. 打破双亲委派的三种现实场景

| 场景 | 代表 | 怎么破 |
|---|---|---|
| SPI：接口在 JDK，实现在第三方 jar | JDBC、JNDI、`ServiceLoader` | 线程上下文类加载器（TCCL）：框架用 `Thread.currentThread().getContextClassLoader()` 反向加载实现 |
| 容器隔离：每个应用有自己的类 | Tomcat 的 WebAppClassLoader | 重写 `loadClass`，先加载自己 WEB-INF/lib 下的类（父最后） |
| 热部署 / 版本共存 | OSGi、Eclipse/IDEA 插件 | 新类加载器 + 丢弃旧加载器（见第五节） |

打破委派演示的关键结论：**类身份 = 全限定名 + 定义它的类加载器**。同一个全限定名被
两个加载器加载后是两个不同的 Class，`instanceof` 不成立，只能通过**反射**或**共同接口**
交互。

### 4. 类冲突（Jar Hell）：两个 jar 有相同类名

先明确冲突的本质：**同一个类加载器对同一个全限定名最多加载一次**，谁先加载谁生效，
后加载的版本被"看不见"（NoSuchMethodError / AbstractMethodError 由此而来）。

解决思路分两类：

**（A）只保留一个版本（Maven 仲裁 / 排除）**——适用于"版本不同但 API 兼容"或
"功能等价只要一份"：
- Maven 依赖仲裁规则：**最近路径优先**（路径越短越优先），同深度**先声明优先**；
- 用 `<dependencyManagement>` 强制指定版本；用 `<exclusions>` 剔除不需要的传递依赖；
- 两个 jar 只是版本不同、二进制兼容 → 直接升到统一高版本即可。

**（B）让两个版本共存（隔离加载）**——适用于"两个 jar 都有使用"：
- 每个 jar 配**一个专属类加载器**（父 = 共同接口所在加载器），同名类就是两个不同的
  Class，互不覆盖；
- **兼容层**：抽象出共同接口（如 `IGreeter`、`PluginVersion`）放在父加载器，两个版本
  都实现它 → 业务代码用接口类型直接调用，无需反射，也没有 ClassCastException；
- 现实对应：Tomcat 多 WebApp 隔离、OSGi 多 bundle、Flink/Spark 的 parent-last、
  slf4j 门面 + 多实现绑定。

**（C）构建期重定位（补充）**：`maven-shade-plugin` 的 relocation 可以把冲突包的包名
整体改写（如 `org.apache.http` → `shaded.org.apache.http`），让两个版本物理上不再同名。

> ⚠️ 注意隔离加载的边界：**全局单例类**（Spring 容器、日志绑定）被双份加载会导致状态
> 分裂 / ClassCastException，这类"全局设施"必须用方案 A 收敛成一份。

### 5. 类加载/卸载机制（两个模块不同时使用）

类可以被卸载的三个条件（**面试高频**）：
1. 该类的 Class 对象没有任何强引用；
2. 定义它的 ClassLoader 没有任何强引用；
3. 该加载器加载的**所有**类都满足条件 1（卸载是整批的，一个加载器下的类要么都活着、
   要么一起被卸载）。

怎么验证卸载：没有 API 能直接查询"类是否已卸载"，标准做法是用 `WeakReference` 持有
Class 对象，清空所有强引用后循环 `System.gc()`，`weakRef.get() == null` 即说明类已被
回收（本模块 `ClassUnloadDemo` 完整演示，卸载后可重新加载并得到全新 Class）。

**回答"两个服务模块不是同时使用，能否使用类的加载卸载机制"：可以。** 做法与插件系统
（`PluginRunner`）一致：每个模块用独立类加载器加载，用完把加载器引用全部置空，GC 即
卸载整个模块的类；切换时用全新加载器加载另一个版本，互不残留、可反复热切换。这正是
Tomcat reload、Spring Boot DevTools、OSGi 动态换 bundle 的原理。若两个模块**同时**使用，
则用 `IsolationDemo` 的"双加载器并存"方案。

## 四、关键 API 速查

| API | 作用 | 本模块演示 |
|---|---|---|
| `ClassLoader.loadClass(name)` | 只加载不初始化 | 生命周期【A】 |
| `Class.forName(name)` | 加载 + 初始化（默认用调用者加载器） | 生命周期【D】 |
| `Class.forName(name, false, loader)` | 只加载 + 链接，不初始化 | 生命周期【B】 |
| `Class.forName(name, true, loader)` | 指定加载器 + 初始化（SPI 加载实现的标准姿势） | `TccLDemo.loadViaTccL` |
| `ClassLoader.findClass(name)` | 子类扩展点：找字节码 → `defineClass` | `FileClassLoader` |
| `ClassLoader.defineClass(name, b, 0, len)` | 字节码 → Class 对象（protected） | `FileClassLoader` |
| `ClassLoader.getSystemClassLoader()` | 应用类加载器 | `DelegationChainDemo` |
| `ClassLoader.getPlatformClassLoader()` | 平台类加载器（JDK 9+） | `DelegationChainDemo` |
| `Thread.setContextClassLoader(...)` | 设置线程上下文类加载器（用后必须恢复） | `TccLDemo` |
| `ToolProvider.getSystemJavaCompiler()` | 运行时编译源码（本模块模拟"两个 jar"的原料） | `RuntimeCompiler` |
| `WeakReference<Class<?>>` | 观测类是否被卸载（get() == null = 已卸载） | `ClassUnloadDemo` |

## 五、常见错误与排查

1. **`ClassCastException: X cannot be cast to X`**——同名类被两个加载器加载，类型不同。
   跨加载器必须用共同接口或反射；用 `-XX:+TraceClassLoading` 看类实际由谁加载。
2. **`NoSuchMethodError` / `AbstractMethodError` 但源码看起来没错**——jar 冲突，运行的
   是"先加载的旧版本"，方法签名对不上。用 `mvn dependency:tree` 查依赖仲裁结果。
3. **`ClassNotFoundException` 但 jar 明明在**——双亲委派：父加载器找不到，你的子加载器
   又没被正确使用（比如忘了用 TCCL）。回顾第四节 SPI 场景。
4. **自定义类加载器里 `java.*` 报 SecurityException**——重写 loadClass 时把所有类都
   "自己先加载"了。`java.*` 必须无条件委派给 Bootstrap。
5. **类卸载验证不到**——检查是否还有强引用：业务代码持有实例、`Class.forName` 结果被
   存了静态字段、加载器被缓存。注意**测试代码自己**别强引用 Class/加载器（本模块测试
   特意用 `WeakReference` 记录加载器）。
6. **`System.gc()` 不保证立即卸载**——类卸载在 GC 阶段发生，演示/测试用循环 + 重试
   （本模块最多 100 次 × 10ms）；生产环境不要依赖卸载的"实时性"，这是尽力而为的回收。
7. **每次 `URLClassLoader` 用完不 close 导致文件句柄泄漏**——能 close 就 close；
   但注意 close 后该类加载器加载的类仍可被使用，只是不能再加载新类。

## 六、测试用例与守护场景（59 个）

| 测试类 | 守护场景 |
|---|---|
| `JarClassLoaderTest`（9） | jar 文件加载、父优先委派、findLoadedClass 缓存、双加载器类身份不同、多 jar 顺序搜索、同名类先声明 jar 先生效、ClassNotFoundException、listAvailableClasses、jar 与目录加载对照（类身份不同但都能用） |
| `ClassLoadingLifecycleDemoTest`（9） | loadClass / forName(false) / 编译期常量不初始化；forName(true) / getstatic / new 触发初始化；`<clinit>` 只执行一次；父先于子初始化；全新加载器 = 全新未初始化类 |
| `DelegationChainDemoTest`（6） | 三层委派链结构；String=Bootstrap、DataSource=Platform、用户类=Application；自定义加载器加载 String 委派到同一份；委派流程伪代码 |
| `FileClassLoaderTest`（7） | 目录加载、父优先委派、findLoadedClass 缓存、双加载器类身份不同、多目录顺序搜索、ClassNotFoundException、listAvailableClasses |
| `BreakDelegationClassLoaderTest`（5） | 委派拿父版本 / 打破拿子版本；类身份不同；java.* 仍委派 Bootstrap；本地缺失回退父 |
| `TccLDemoTest`（5） | 不用 TCCL 找不到；用 TCCL 找到并 cast 接口；TCCL 用错加载器仍失败；用后恢复 TCCL；实现类=child、接口=parent |
| `ClassConflictDemoTest`（4） | classpath 顺序决定谁生效（A 前 / B 前）；同一加载器只留一份；赢家实现共同接口可安全调用 |
| `IsolationDemoTest`（4） | 双版本同时可用；类身份不同；跨加载器 instanceof 失败；共同接口父加载器提供可安全 cast |
| `ClassUnloadDemoTest`（4） | 刚加载可用；清引用 + GC 后卸载；卸载后可重新加载（全新加载器）；加载器存活时类不卸载 |
| `PluginRunnerTest`（6） | v1/v2 各自执行正确；用完卸载；顺序切换 v1→v2 全新加载器；反复热切换；同时部署双版本共存 |

## 七、动手练习

1. ✅ **已完成——改造类加载器支持 jar 文件**：见 `delegation.custom.JarClassLoader`
   （传 `.jar` 路径，用 `JarFile` 读 `.class` 字节码再 `defineClass`）及其测试
   `JarClassLoaderTest`。对照结论：jar 与目录只是字节码来源不同，加载原理一致；
   多 jar 顺序搜索与真实 classpath 的"先声明先生效"规则相同。
2. **给 PluginRunner 增加卸载钩子**：在 `PluginVersion` 上加 `void shutdown()`，
   unload 前调用，模拟"停模块前释放资源"。
3. **写一个 ServiceLoader 版 SPI**：用 `META-INF/services` 文件 + `ServiceLoader` 替换
   `TccLDemo` 的手工 `Class.forName`，观察谁在用 TCCL。
4. **观察真实热部署**：用 `java -cp` 起一个循环加载 PluginRunner 的进程，配合
   `-XX:+TraceClassLoading` 观察类加载日志，再对比卸载前后 `jcmd GC.class_histogram`
   里的类数量变化。
5. **用 Maven 实测仲裁**：在某个模块加两个不同版本的同坐标依赖，跑
   `mvn dependency:tree` 观察"最近优先/先声明优先"的裁决结果，再用 `<exclusions>` 和
   `dependencyManagement` 改写。
6. **防 OOM 的类加载器**：用 `WeakHashMap<ClassLoader, ...>` 缓存插件加载器，演示
   "用不到就自动回收"的插件注册表。

## 八、已知限制

- 运行时编译（`RuntimeCompiler`）依赖 `jdk.compiler` 模块，纯 JRE 环境跑不了（测试和
  Demo 需要 JDK）；
- 类卸载的**时机**依赖 GC（本机 G1 下循环 1~3 次即可观察到），测试用循环重试保证稳定，
  但极端 GC 配置（如 `-XX:+DisableExplicitGC`、部分 ZGC 参数）下可能观察不到，属正常；
- 演示中的"jar"是解压目录，真实场景换成 `URLClassLoader` 指向 jar 文件即可，原理一致；
- 隔离加载不适用于全局单例类（Spring 容器、日志绑定），这类必须 Maven 仲裁收敛成一份。

## 九、验证

- `mvn test -pl module-19-classloader`：59 个测试全绿。
- `java -cp target/classes com.study.classloader.Main`：9 个小节全部演示成功
  （生命周期 0/1 次数、委派链三层、委派/打破对照 parent-version vs child-version、
  TCCL 加载实现、冲突 A/B 切换、隔离双版本共存、类卸载 true、插件 v1→v2→v1 热切换、
  jar 加载 vs 目录加载对照 false）。
- 全量 `mvn clean verify`：BUILD SUCCESS、0 checkstyle 违规。