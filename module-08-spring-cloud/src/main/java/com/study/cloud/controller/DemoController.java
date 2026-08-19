package com.study.cloud.controller;

import com.study.cloud.client.GithubClient;
import com.study.cloud.service.ResilientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示接口
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final ResilientService resilientService;
    private final GithubClient githubClient;

    public DemoController(ResilientService resilientService, GithubClient githubClient) {
        this.resilientService = resilientService;
        this.githubClient = githubClient;
    }

    /**
     * Feign + 熔断演示：连续调用观察熔断器状态变化
     * curl "http://localhost:8080/api/demo/call?name=张三"
     */
    @GetMapping("/call")
    public Map<String, Object> call(@RequestParam String name) {
        return resilientService.callRemote(name);
    }

    /**
     * Feign 调用真实 GitHub API
     * curl http://localhost:8080/api/demo/github/octocat
     */
    @GetMapping("/github/{username}")
    public Map<String, Object> github(@PathVariable String username) {
        return githubClient.getUser(username);
    }
}
