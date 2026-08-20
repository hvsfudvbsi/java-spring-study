package com.study.advanced.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CacheService 纯单元测试：验证业务存取逻辑；缓存代理行为由 CacheServiceTest 验证。
 */
class CacheServiceUnitTest {

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService();
    }

    @Test
    void getUserShouldCreateDefaultValue() {
        assertThat(cacheService.getUser(1L)).isEqualTo("用户1");
    }

    @Test
    void updateUserShouldReplaceValue() {
        cacheService.getUser(2L);

        assertThat(cacheService.updateUser(2L, "新用户2")).isEqualTo("新用户2");
        assertThat(cacheService.getUser(2L)).isEqualTo("新用户2");
    }

    @Test
    void deleteUserShouldAllowFreshValueToBeCreated() {
        cacheService.updateUser(3L, "旧用户3");

        cacheService.deleteUser(3L);

        assertThat(cacheService.getUser(3L)).isEqualTo("用户3");
    }

    @Test
    void clearCacheShouldKeepServiceUsable() {
        cacheService.updateUser(4L, "用户4");

        cacheService.clearCache();

        assertThat(cacheService.getUser(4L)).isEqualTo("用户4");
    }
}
