package com.study.bootbasics.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 条件化 Bean：只有满足条件时才创建（自动配置的核心机制）
 *
 * 本例：只有配置了 app.feature.hello-enabled=true 时，这个 Bean 才存在。
 *
 * 常用条件注解（org.springframework.boot.autoconfigure.condition 包）：
 *   @ConditionalOnProperty   配置项是否匹配
 *   @ConditionalOnClass      类路径是否有某个类
 *   @ConditionalOnMissingBean   容器中是否缺少某个 Bean
 *   @ConditionalOnWebApplication 是否是 Web 应用
 */
@Component
@ConditionalOnProperty(name = "app.feature.hello-enabled", havingValue = "true", matchIfMissing = true)
public class ConditionalService {

    private static final Logger log = LoggerFactory.getLogger(ConditionalService.class);

    public ConditionalService() {
        log.info("ConditionalService 已创建（app.feature.hello-enabled=true）");
    }

    public String featureInfo() {
        return "条件化功能已开启";
    }
}
