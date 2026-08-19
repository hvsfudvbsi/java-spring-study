package com.study.mvc.service;

import com.study.mvc.exception.UserNotFoundException;
import com.study.mvc.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务层：内存版用户服务（不连数据库，JPA 见 module-04）
 *
 * 分层职责：
 *   Controller  -> 接收请求/参数校验/返回响应（不写业务逻辑）
 *   Service     -> 业务逻辑/事务边界（本示例）
 *   Repository  -> 数据访问（module-04 的 JPA）
 */
@Service
public class UserService {

    /** 模拟数据库：ConcurrentHashMap 保证线程安全 */
    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public User create(User user) {
        Long id = idGenerator.getAndIncrement();
        User saved = new User(id, user.name(), user.email(), user.age(), user.phone());
        store.put(id, saved);
        return saved;
    }

    public User getById(Long id) {
        User user = store.get(id);
        if (user == null) {
            throw new UserNotFoundException(id);
        }
        return user;
    }

    public List<User> list(int page, int size) {
        // 简单分页：跳过前 page*size 条，取 size 条
        return new ArrayList<>(store.values()).stream()
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();
    }

    public long count() {
        return store.size();
    }

    public User update(Long id, User user) {
        if (!store.containsKey(id)) {
            throw new UserNotFoundException(id);
        }
        User updated = new User(id, user.name(), user.email(), user.age(), user.phone());
        store.put(id, updated);
        return updated;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new UserNotFoundException(id);
        }
    }
}
