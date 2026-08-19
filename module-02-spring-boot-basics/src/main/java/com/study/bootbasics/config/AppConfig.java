package com.study.bootbasics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.function.Supplier;

/**
 * 显式配置类：用 @Bean 手动声明 Bean（区别于 @Component 扫描）
 *
 * 适用场景：
 *   - 第三方库的类无法加 @Component 注解时（如 RestTemplate、ObjectMapper）
 *   - 需要根据条件/参数动态创建 Bean 时
 *   - 需要精确控制初始化逻辑时
 *
 * Bean 生命周期（面试必问）：
 *   实例化 -> 属性填充 -> 初始化（@PostConstruct / InitializingBean.afterPropertiesSet）
 *   -> 使用中 -> 销毁（@PreDestroy / DisposableBean.destroy）
 */
@Configuration
public class AppConfig {

    /**
     * 声明第三方类的 Bean：Spring 无法给 JDK 类加注解，只能靠 @Bean
     */
    @Bean
    public Supplier<String> appInfoSupplier(AppProperties props) {
        // 方法参数会由 Spring 自动注入（依赖注入的体现）
        return () -> props.name() + " v" + props.version() + " by " + props.author();
    }

    /**
     * @Primary：当同类型有多个 Bean 时，标记为优先注入
     */
    @Bean
    @Primary
    public Supplier<String> defaultInfoSupplier() {
        return () -> "默认应用信息";
    }

    /**
     * @Bean(name = "...") 可以自定义 Bean 名称，默认是方法名
     */
    @Bean(name = "greetingTemplate")
    public String greetingTemplate() {
        return "你好，%s！";
    }
}
