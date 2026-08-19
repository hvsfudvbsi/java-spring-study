package com.study.aop.aspect;

import com.study.aop.annotation.LogExecutionTime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 性能监控切面：统计 @LogExecutionTime 标注方法的执行时间
 *
 * 切入点表达式：@annotation(com.study.aop.annotation.LogExecutionTime)
 *   -> 匹配所有标注了该注解的方法
 *
 * @Around 环绕通知：最强大，可以控制方法执行前/后/是否执行/替换返回值
 * 必须调用 proceedingJoinPoint.proceed() 才会执行目标方法！
 */
@Aspect
@Component
public class PerformanceAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceAspect.class);

    @Around("@annotation(com.study.aop.annotation.LogExecutionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            // 执行目标方法
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("[性能监控] {}.{}() 耗时 {} ms",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    elapsed);
        }
    }
}
