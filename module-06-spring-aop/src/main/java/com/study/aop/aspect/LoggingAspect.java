package com.study.aop.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 日志切面：展示五种通知类型
 *
 * 切入点表达式（execution）详解：
 *   execution(修饰符 返回类型 类路径.方法名(参数))
 *
 *   execution(* com.study.aop.service..*.*(..))
 *     |      |           |         |  |
 *     |      |           |         |  +-- (..) 任意参数
 *     |      |           |         +----- 任意方法名
 *     |      |           +--------------- service 包及其子包（..）
 *     |      +---------------------------- 任意返回类型
 *     +----------------------------------- 任意修饰符
 *
 * 通知类型：
 *   @Before          方法执行前
 *   @After           方法执行后（无论是否异常，类似 finally）
 *   @AfterReturning  方法正常返回后
 *   @AfterThrowing   方法抛异常后
 *   @Around          环绕（见 PerformanceAspect）
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /** 切入点定义：复用表达式 */
    @org.aspectj.lang.annotation.Pointcut("execution(* com.study.aop.service.*.*(..))")
    public void serviceMethods() {
    }

    /** 方法执行前：记录方法名和参数 */
    @Before("serviceMethods()")
    public void before(JoinPoint joinPoint) {
        log.info("[日志-前] 调用 {}.{}()，参数: {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                java.util.Arrays.toString(joinPoint.getArgs()));
    }

    /** 方法正常返回后：记录返回值 */
    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        log.info("[日志-返回] {}.{}() 返回值: {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                result);
    }

    /** 方法抛异常后：记录异常 */
    @AfterThrowing(pointcut = "serviceMethods()", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Exception ex) {
        log.warn("[日志-异常] {}.{}() 抛出异常: {}",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName(),
                ex.getMessage());
    }

    /** 方法结束后（无论成败） */
    @After("serviceMethods()")
    public void after(JoinPoint joinPoint) {
        log.info("[日志-结束] {}.{}() 执行完毕",
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName());
    }
}
