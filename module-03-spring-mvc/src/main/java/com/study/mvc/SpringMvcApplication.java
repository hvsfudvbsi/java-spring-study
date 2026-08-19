package com.study.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring MVC 学习模块入口
 *
 * 请求处理流程（面试必问）：
 *   DispatcherServlet 接收请求
 *     -> HandlerMapping 找到对应的 @RequestMapping 方法
 *     -> HandlerAdapter 调用 Controller 方法（先过拦截器 Interceptor）
 *     -> 返回值经 HttpMessageConverter 转成 JSON
 *     -> 异常交给 @ControllerAdvice 全局处理
 */
@SpringBootApplication
public class SpringMvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringMvcApplication.class, args);
    }
}
