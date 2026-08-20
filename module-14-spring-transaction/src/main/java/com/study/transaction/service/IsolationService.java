package com.study.transaction.service;

import com.study.transaction.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 事务隔离级别、只读事务和超时属性示例。 */
@Service
public class IsolationService {

    private final AccountRepository accountRepository;

    public IsolationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** 使用 REPEATABLE_READ 隔离级别读取账户。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void readWithIsolation(Long accountId) {
        accountRepository.findById(accountId);
    }

    /** 使用 readOnly=true 执行查询。 */
    @Transactional(readOnly = true)
    public void readOnlyTransaction() {
        accountRepository.findAll();
    }

    /** 使用 timeout=3 秒执行查询。 */
    @Transactional(timeout = 3)
    public void withTimeout(Long accountId) {
        accountRepository.findById(accountId);
    }
}
