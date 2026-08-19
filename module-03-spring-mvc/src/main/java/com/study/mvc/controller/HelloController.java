package com.study.mvc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 参数绑定演示：展示 MVC 各种取参数的方式
 *
 * 示例 URL：
 *   GET /api/demo/hello/张三?lang=cn&verbose=true
 *   GET /api/demo/headers
 */
@RestController
@RequestMapping("/api/demo")
public class HelloController {

    /** 路径参数 + 查询参数 + 默认值 + 数组参数 */
    @GetMapping("/hello/{name}")
    public Map<String, Object> hello(
            @PathVariable String name,
            @RequestParam(defaultValue = "cn") String lang,
            @RequestParam(defaultValue = "false") boolean verbose,
            @RequestParam(required = false) String[] tags) {

        String greeting = "cn".equals(lang) ? "你好" : "Hello";
        return Map.of(
                "message", greeting + ", " + name + "!",
                "verbose", verbose,
                "tags", tags == null ? new String[0] : tags
        );
    }

    /** 读取请求头 */
    @GetMapping("/headers")
    public Map<String, String> headers(
            @RequestHeader(value = "User-Agent", defaultValue = "unknown") String userAgent,
            @RequestHeader(value = "Accept-Language", defaultValue = "unknown") String acceptLanguage) {
        return Map.of(
                "user-agent", userAgent,
                "accept-language", acceptLanguage
        );
    }
}
