package com.study.transaction.service;

import com.study.transaction.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 扣款和入账必须在同一个事务中：任一步失败，前面的数据库操作也会回滚。
     */
    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("转出账户和转入账户不能相同");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("转账金额必须大于 0");
        }

        if (accountRepository.withdraw(fromId, amount) == 0) {
            throw new IllegalStateException("转出账户不存在或余额不足");
        }
        if (accountRepository.deposit(toId, amount) == 0) {
            throw new IllegalStateException("转入账户不存在");
        }
    }
}
