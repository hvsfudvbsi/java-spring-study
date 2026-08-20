package com.study.transaction.service;

import com.study.transaction.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 转账事务示例：对比正常提交、运行时异常回滚和受检异常默认不回滚。 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** 两次 SQL 在同一个事务中成功提交。 */
    @Transactional
    public void transferSuccess(Long fromId, Long toId, BigDecimal amount) {
        validate(fromId, toId, amount);
        withdraw(fromId, amount);
        deposit(toId, amount);
    }

    /** 先扣款再抛运行时异常，默认触发整个事务回滚。 */
    @Transactional
    public void transferWithRuntimeException(Long fromId, Long toId, BigDecimal amount) {
        validate(fromId, toId, amount);
        withdraw(fromId, amount);
        throw new IllegalStateException("运行时异常：转账失败");
    }

    /** 先扣款再抛受检异常，默认不会触发 Spring 事务回滚。 */
    @Transactional
    public void transferWithCheckedException(Long fromId, Long toId, BigDecimal amount) throws Exception {
        validate(fromId, toId, amount);
        withdraw(fromId, amount);
        throw new Exception("受检异常：转账失败");
    }

    private void validate(Long fromId, Long toId, BigDecimal amount) {
        if (fromId == null || toId == null || fromId.equals(toId)) {
            throw new IllegalArgumentException("转出账户和转入账户不能相同且不能为空");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("转账金额必须大于 0");
        }
    }

    private void withdraw(Long accountId, BigDecimal amount) {
        if (accountRepository.withdraw(accountId, amount) == 0) {
            throw new IllegalStateException("转出账户不存在或余额不足");
        }
    }

    private void deposit(Long accountId, BigDecimal amount) {
        if (accountRepository.deposit(accountId, amount) == 0) {
            throw new IllegalStateException("转入账户不存在");
        }
    }
}
