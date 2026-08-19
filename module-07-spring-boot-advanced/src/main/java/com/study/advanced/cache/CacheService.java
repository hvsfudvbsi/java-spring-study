package com.study.advanced.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存服务：演示注解式缓存（@EnableCaching + Caffeine）
 *
 * 缓存注解：
 *   @Cacheable      先查缓存，命中直接返回；未命中执行方法并缓存结果
 *   @CachePut       总是执行方法，并用返回值更新缓存（适合"更新"操作）
 *   @CacheEvict     清除缓存（适合"删除"操作）
 *
 * 缓存 key 规则（SpEL）：
 *   #id           参数名
 *   #p0           第一个参数
 *   #root.methodName  方法名
 *
 * 使用注意：
 *   - 同类内部调用不经过代理，缓存注解失效（与 @Transactional 同款陷阱）
 *   - 缓存的对象建议不可变
 *   - 缓存穿透/击穿/雪崩是面试重点，可配合布隆过滤器/互斥锁解决
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    /** 模拟数据库 */
    private final Map<Long, String> store = new ConcurrentHashMap<>();

    /**
     * 查询用户：第一次查库并缓存，之后直接命中缓存
     * 访问 http://localhost:8080/api/user/1 两次，观察日志只输出一次"查询数据库"
     */
    @Cacheable(cacheNames = "userCache", key = "#id")
    public String getUser(Long id) {
        log.info(">>> 查询数据库: user id={}（第二次访问应看不到这行日志）", id);
        return store.computeIfAbsent(id, k -> "用户" + k);
    }

    /** 更新用户：总是执行方法并刷新缓存 */
    @CachePut(cacheNames = "userCache", key = "#id")
    public String updateUser(Long id, String name) {
        log.info(">>> 更新用户: id={}, name={}", id, name);
        store.put(id, name);
        return name;
    }

    /** 删除用户：同时清除缓存 */
    @CacheEvict(cacheNames = "userCache", key = "#id")
    public void deleteUser(Long id) {
        log.info(">>> 删除用户: id={}，缓存已清除", id);
        store.remove(id);
    }

    /** 清空整个 userCache */
    @CacheEvict(cacheNames = "userCache", allEntries = true)
    public void clearCache() {
        log.info(">>> 清空 userCache");
    }
}
