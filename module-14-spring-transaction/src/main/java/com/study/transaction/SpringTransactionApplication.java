package com.study.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring 事务学习模块入口。
 *
 * 本模块使用 JdbcTemplate + H2，把事务边界直接落到数据库提交、回滚上，
 * 方便观察 REQUIRED、REQUIRES_NEW 和 NESTED 的差异。
 */
@SpringBootApplication
public class SpringTransactionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringTransactionApplication.class, args);
    }
}
