package com.study.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解 + AOP 组合：标记哪些方法需要统计执行时间
 *
 * 用法：在任意方法上加 @LogExecutionTime 即可，无需修改业务代码
 *
 * 注解三要素：
 *   @Target    注解可以用在哪里（METHOD 方法上）
 *   @Retention 保留策略（RUNTIME 运行时可见，AOP 需要反射读取）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
}
