package com.study.transaction.service;

import com.study.transaction.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("转账：先扣款后入账，两次仓库调用顺序正确")
    void transferShouldWithdrawThenDeposit() {
        when(accountRepository.withdraw(1L, new BigDecimal("30.00"))).thenReturn(1);
        when(accountRepository.deposit(2L, new BigDecimal("30.00"))).thenReturn(1);

        accountService.transfer(1L, 2L, new BigDecimal("30.00"));

        verify(accountRepository).withdraw(1L, new BigDecimal("30.00"));
        verify(accountRepository).deposit(2L, new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("同账户转账：在仓库调用前被拒绝")
    void sameAccountShouldBeRejectedBeforeRepositoryCalls() {
        assertThatThrownBy(() -> accountService.transfer(1L, 1L, new BigDecimal("1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("转出账户和转入账户不能相同");
    }

    @Test
    @DisplayName("非正金额：在仓库调用前被拒绝")
    void nonPositiveAmountShouldBeRejected() {
        assertThatThrownBy(() -> accountService.transfer(1L, 2L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("转账金额必须大于 0");
    }

    @Test
    @DisplayName("余额不足：扣款返回 0，入账前即失败")
    void insufficientBalanceShouldStopBeforeDeposit() {
        when(accountRepository.withdraw(1L, new BigDecimal("30.00"))).thenReturn(0);

        assertThatThrownBy(() -> accountService.transfer(1L, 2L, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("转出账户不存在或余额不足");
    }

    @Test
    @DisplayName("转入账户不存在：扣款成功后入账失败")
    void missingRecipientShouldFailAfterWithdraw() {
        when(accountRepository.withdraw(1L, new BigDecimal("30.00"))).thenReturn(1);
        when(accountRepository.deposit(2L, new BigDecimal("30.00"))).thenReturn(0);

        assertThatThrownBy(() -> accountService.transfer(1L, 2L, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("转入账户不存在");
    }
}
