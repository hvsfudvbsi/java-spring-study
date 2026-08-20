package com.study.cloud.service;

import com.study.cloud.client.RemoteServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ResilientService 纯单元测试；熔断器状态机由 Spring 集成配置负责验证。 */
class ResilientServiceUnitTest {

    private RemoteServiceClient remoteServiceClient;
    private ResilientService resilientService;

    @BeforeEach
    void setUp() {
        remoteServiceClient = mock(RemoteServiceClient.class);
        resilientService = new ResilientService(remoteServiceClient);
    }

    @Test
    @DisplayName("callRemote 将入参透传给 Feign 客户端并返回其结果")
    void callRemoteShouldDelegateToFeignClient() {
        Map<String, Object> response = Map.of("message", "hello", "status", "OK");
        when(remoteServiceClient.callHello("张三")).thenReturn(response);

        assertThat(resilientService.callRemote("张三")).isEqualTo(response);
        verify(remoteServiceClient).callHello("张三");
    }

    @Test
    @DisplayName("fallback 返回固定降级响应（含请求名与降级说明）")
    void fallbackShouldReturnStableDegradedResponse() {
        Map<String, Object> response = resilientService.fallback("张三", new RuntimeException("timeout"));

        assertThat(response)
                .containsEntry("status", "FALLBACK")
                .containsEntry("requestedName", "张三")
                .containsEntry("message", "服务暂时不可用，已降级返回（缓存/默认值）");
    }
}
