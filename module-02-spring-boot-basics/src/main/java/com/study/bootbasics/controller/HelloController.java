package com.study.bootbasics.controller;

import com.study.bootbasics.component.ConditionalService;
import com.study.bootbasics.component.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最简单的 REST 接口（MVC 的详细知识见 module-03）
 *
 * 启动后访问：
 *   http://localhost:8080/hello/world
 *   http://localhost:8080/config
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    private final GreetingService greetingService;
    private final ConditionalService conditionalService;

    public HelloController(GreetingService greetingService, ConditionalService conditionalService) {
        this.greetingService = greetingService;
        this.conditionalService = conditionalService;
    }

    @GetMapping("/hello/{name}")
    public String hello(@PathVariable String name) {
        return greetingService.greet(name);
    }

    @GetMapping("/config")
    public String config() {
        return greetingService.showConfig() + " || " + conditionalService.featureInfo();
    }
}
