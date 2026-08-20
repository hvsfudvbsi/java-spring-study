package com.study.cloud.service;

import com.study.cloud.client.RemoteServiceClient;
import org.junit.jupiter.api.BeforeEach;
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
    void callRemoteShouldDelegateToFeignClient() {
        Map<String, Object> response = Map.of("message", "hello", "status", "OK");
        when(remoteServiceClient.callHello("张三")).thenReturn(response);

        assertThat(resilientService.callRemote("张三")).isEqualTo(response);
        verify(remoteServiceClient).callHello("张三");
    }

    @Test
    void fallbackShouldReturnStableDegradedResponse() {
        Map<String, Object> response = resilientService.fallback("张三", new RuntimeException("timeout"));

        assertThat(response)
                .containsEntry("status", "FALLBACK")
                .containsEntry("requestedName", "张三")
                .containsEntry("message", "服务暂时不可用，已降级返回（缓存/默认值）");
    }
}
