package com.study.cloud.controller;

import com.study.cloud.client.GithubClient;
import com.study.cloud.service.ResilientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DemoController 纯单元测试：不启动 Feign、负载均衡或熔断器。 */
class DemoControllerUnitTest {

    private ResilientService resilientService;
    private GithubClient githubClient;
    private DemoController controller;

    @BeforeEach
    void setUp() {
        resilientService = mock(ResilientService.class);
        githubClient = mock(GithubClient.class);
        controller = new DemoController(resilientService, githubClient);
    }

    @Test
    void callShouldDelegateToResilientService() {
        Map<String, Object> response = Map.of("status", "OK");
        when(resilientService.callRemote("张三")).thenReturn(response);

        assertThat(controller.call("张三")).isEqualTo(response);
        verify(resilientService).callRemote("张三");
    }

    @Test
    void githubShouldDelegateToFeignClient() {
        Map<String, Object> response = Map.of("login", "octocat");
        when(githubClient.getUser("octocat")).thenReturn(response);

        assertThat(controller.github("octocat")).isEqualTo(response);
        verify(githubClient).getUser("octocat");
    }
}
