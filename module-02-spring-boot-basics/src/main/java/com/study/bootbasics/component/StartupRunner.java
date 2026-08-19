package com.study.bootbasics.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * CommandLineRunner：Spring Boot 启动完成后执行一次（常用于初始化数据、打印启动信息）
 *
 * 执行时机：容器刷新完成、所有 Bean 初始化之后。
 * 如果有多个 Runner，可以用 @Order 控制执行顺序。
 *
 * 对比：
 *   CommandLineRunner.run(String... args)     接收原始命令行参数
 *   ApplicationRunner.run(ApplicationArguments) 接收解析后的参数（更强大）
 */
@Component
public class StartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    @Override
    public void run(String... args) {
        log.info("========== 应用启动完成，命令行参数: {} ==========", String.join(", ", args));
    }
}
