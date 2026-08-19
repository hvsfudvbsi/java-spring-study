package com.study.jpa.repository;

import com.study.jpa.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository 数据访问层：只需继承 JpaRepository 即可获得大量内置方法
 *
 * 内置能力（无需写 SQL）：
 *   save / findById / findAll / deleteById / count / existsById
 *   findAll(Pageable) 分页 / findAll(Sort) 排序
 *
 * 派生查询：根据方法名自动生成 SQL（Spring Data 解析方法名）
 *   findByEmail          -> SELECT * FROM t_user WHERE email = ?
 *   findByNameContaining -> WHERE name LIKE '%' ? '%'
 *   countByAgeGreaterThan -> COUNT WHERE age > ?
 *
 * 自定义查询：@Query 写 JPQL 或 native SQL
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 派生查询：按邮箱精确查找 */
    Optional<User> findByEmail(String email);

    /** 派生查询：名称模糊匹配（忽略大小写） */
    List<User> findByNameContainingIgnoreCase(String keyword);

    /** 派生查询：按年龄范围 */
    List<User> findByAgeBetween(int min, int max);

    /** 派生查询：统计某年龄段人数 */
    long countByAgeGreaterThan(int age);

    /** 自定义 JPQL：JPQL 操作的是实体对象（User），不是表 */
    @Query("select u from User u where u.email like %:domain%")
    List<User> findByEmailDomain(@Param("domain") String domain);

    /** 分页查询 + 名称模糊匹配（Pageable 由 Spring Data 自动填充） */
    Page<User> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /** 修改操作需要 @Modifying + 事务 */
    @Modifying
    @Transactional
    @Query("update User u set u.age = :age where u.email = :email")
    int updateAgeByEmail(@Param("email") String email, @Param("age") Integer age);
}
