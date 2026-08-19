package com.study.jpa.repository;

import com.study.jpa.config.JpaAuditingConfig;
import com.study.jpa.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @DataJpaTest：只加载 JPA 相关组件（不加载 Controller/Service），
 * 使用 H2 内存库，每个测试自动回滚事务（互不影响）。
 * 这是测试 Repository 的标准方式。
 *
 * 注意：@DataJpaTest 是切片测试，默认不加载 @Configuration 类，
 * 所以 @EnableJpaAuditing（审计字段自动填充）需要 @Import 显式导入。
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("张三", "zhangsan@example.com", 25));
        userRepository.save(new User("李四", "lisi@example.com", 30));
        userRepository.save(new User("王五", "wangwu@other.com", 18));
    }

    @Test
    @DisplayName("派生查询：按邮箱精确查找")
    void findByEmail() {
        Optional<User> found = userRepository.findByEmail("zhangsan@example.com");
        assertTrue(found.isPresent());
        assertEquals("张三", found.get().getName());
    }

    @Test
    @DisplayName("派生查询：名称模糊匹配")
    void findByNameContaining() {
        List<User> result = userRepository.findByNameContainingIgnoreCase("张");
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("派生查询：按年龄范围")
    void findByAgeBetween() {
        List<User> result = userRepository.findByAgeBetween(18, 25);
        assertEquals(2, result.size()); // 张三25、王五18
    }

    @Test
    @DisplayName("自定义 JPQL：按邮箱域名查询")
    void findByEmailDomain() {
        List<User> result = userRepository.findByEmailDomain("example.com");
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("审计字段自动填充")
    void auditingFieldsAreAutoFilled() {
        User user = userRepository.findByEmail("zhangsan@example.com").orElseThrow();
        assertTrue(user.getCreatedAt() != null, "createdAt 应由审计自动填充");
    }

    @Test
    @DisplayName("删除后查不到")
    void deleteUser() {
        User user = userRepository.findByEmail("lisi@example.com").orElseThrow();
        userRepository.delete(user);
        assertFalse(userRepository.findByEmail("lisi@example.com").isPresent());
    }
}
