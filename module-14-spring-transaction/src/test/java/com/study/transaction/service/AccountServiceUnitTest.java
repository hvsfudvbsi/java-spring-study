package com.study.transaction.service;

import com.study.transaction.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AccountService 纯单元测试：验证业务分支；真实提交/回滚由 TransactionServiceTest 验证。
 */
class AccountServiceUnitTest {

    private AccountRepository accountRepository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        accountService = new AccountService(accountRepository);
    }

    @Test
    void transferShouldWithdrawThenDeposit() {
        when(accountRepository.withdraw(1L, new BigDecimal("30.00"))).thenReturn(1);
        when(accountRepository.deposit(2L, new BigDecimal("30.00"))).thenReturn(1);

        accountService.transfer(1L, 2L, new BigDecimal("30.00"));

        verify(accountRepository).withdraw(1L, new BigDecimal("30.00"));
        verify(accountRepository).deposit(2L, new BigDecimal("30.00"));
    }

    @Test
    void sameAccountShouldBeRejectedBeforeRepositoryCalls() {
        assertThatThrownBy(() -> accountService.transfer(1L, 1L, new BigDecimal("1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("转出账户和转入账户不能相同");
    }

    @Test
    void nonPositiveAmountShouldBeRejected() {
        assertThatThrownBy(() -> accountService.transfer(1L, 2L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("转账金额必须大于 0");
    }

    @Test
    void insufficientBalanceShouldStopBeforeDeposit() {
        when(accountRepository.withdraw(1L, new BigDecimal("30.00"))).thenReturn(0);

        assertThatThrownBy(() -> accountService.transfer(1L, 2L, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("转出账户不存在或余额不足");
    }

    @Test
    void missingRecipientShouldFailAfterWithdraw() {
        when(accountRepository.withdraw(1L, new BigDecimal("30.00"))).thenReturn(1);
        when(accountRepository.deposit(2L, new BigDecimal("30.00"))).thenReturn(0);

        assertThatThrownBy(() -> accountService.transfer(1L, 2L, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("转入账户不存在");
    }
}
