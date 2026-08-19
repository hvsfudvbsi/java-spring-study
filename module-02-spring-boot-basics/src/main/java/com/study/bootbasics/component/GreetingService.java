package com.study.bootbasics.component;

import com.study.bootbasics.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @Service 业务层组件（@Component 的语义化别名）
 *
 * 组件扫描体系：
 *   @Component      通用组件
 *   @Service        业务层
 *   @Repository     DAO 层（异常会转换为 Spring 的 DataAccessException）
 *   @Controller     MVC 控制层
 *
 * 依赖注入的三种方式：
 *   1. 构造器注入（推荐，final 字段 + 不可变，便于测试）
 *   2. Setter 注入（可选依赖）
 *   3. 字段注入 @Autowired（不推荐：难以测试、隐藏依赖）
 */
@Service
public class GreetingService {

    /** @Value：注入单个配置项（简单场景够用） */
    @Value("${app.name}")
    private String appName;

    /** 构造器注入：@ConfigurationProperties 配置对象 */
    private final AppProperties appProperties;

    public GreetingService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String greet(String name) {
        return "Hello, %s! 欢迎学习 %s (作者: %s)".formatted(name, appName, appProperties.author());
    }

    /** 展示从配置中读取 List 和 Map 结构 */
    public String showConfig() {
        return "标签: " + appProperties.tags()
                + "，限制: " + appProperties.limits();
    }
}
