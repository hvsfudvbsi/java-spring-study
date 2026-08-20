package com.study.transaction.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 事务基础设施配置。
 *
 * 显式使用 DataSourceTransactionManager，是为了让 JDBC 保存点（NESTED）
 * 的行为在学习示例中清晰可见；生产项目应根据实际数据访问技术选择事务管理器。
 */
@Configuration
public class TransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionManager.setNestedTransactionAllowed(true);
        return transactionManager;
    }
}
