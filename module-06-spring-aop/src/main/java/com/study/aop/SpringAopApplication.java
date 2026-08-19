package com.study.aop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AOP 学习模块入口
 *
 * AOP（面向切面编程）核心概念（面试必问）：
 *   Aspect     切面：横切逻辑的模块化（如日志切面）
 *   Pointcut   切入点：匹配哪些方法（execution 表达式 / @annotation）
 *   Advice     通知：在切入点执行的逻辑（Before / After / Around...）
 *   JoinPoint  连接点：被拦截的方法调用
 *   Weaving    织入：把切面应用到目标对象（Spring 用动态代理）
 *
 * 典型应用场景：日志记录、性能监控、事务管理、权限校验、缓存
 */
@SpringBootApplication
public class SpringAopApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAopApplication.class, args);
    }
}
