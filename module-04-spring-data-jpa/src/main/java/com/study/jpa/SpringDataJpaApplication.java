package com.study.jpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Data JPA 学习模块入口
 *
 * 分层体系：
 *   Entity（实体）-> Repository（数据访问）-> Service（事务）-> Controller（接口）
 *
 * JPA 核心概念（面试必问）：
 *   - 对象关系映射（ORM）：Java 对象 <-> 数据库表
 *   - 一级缓存：同一个 EntityManager 内，同一 id 只查一次数据库
 *   - 懒加载 vs 急加载：@OneToMany(fetch = LAZY) 默认懒加载
 *   - 脏检查：事务提交时自动比对快照，只更新变化字段
 */
@SpringBootApplication
public class SpringDataJpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringDataJpaApplication.class, args);
    }
}
