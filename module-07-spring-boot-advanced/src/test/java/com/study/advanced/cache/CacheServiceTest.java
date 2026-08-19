package com.study.advanced.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 缓存行为测试：
 * 1. 第一次 getUser 会执行方法（查"数据库"）
 * 2. 第二次 getUser 命中缓存（方法不执行，值不变）
 * 3. updateUser 用 @CachePut 更新缓存
 */
@SpringBootTest
class CacheServiceTest {

    @Autowired
    private CacheService cacheService;

    @Test
    void cacheHit_shouldReturnSameInstance() {
        String first = cacheService.getUser(1L);
        String second = cacheService.getUser(1L);

        // 缓存命中时返回同一个对象（Caffeine 缓存的是对象引用）
        assertEquals(first, second);
        assertEquals("用户1", first);
    }

    @Test
    void cachePut_shouldUpdateCache() {
        cacheService.getUser(2L);          // 缓存 用户2
        cacheService.updateUser(2L, "新用户2"); // @CachePut 更新缓存

        // 更新后再次读取，应拿到新值（说明缓存被刷新，而不是旧值）
        assertEquals("新用户2", cacheService.getUser(2L));
    }

    @Test
    void cacheEvict_shouldRemoveEntry() {
        cacheService.getUser(3L);          // 缓存 用户3
        cacheService.deleteUser(3L);       // @CacheEvict 清除缓存

        // 删除后再读取会重新执行方法（再次"查询数据库"生成新值）
        assertEquals("用户3", cacheService.getUser(3L));
    }
}
