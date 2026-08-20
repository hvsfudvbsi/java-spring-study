package com.study.transaction;

import com.study.transaction.repository.AccountRepository;
import com.study.transaction.service.TxInvalidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TxInvalidServiceTest {

    @Autowired
    private TxInvalidService txInvalidService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void resetBalances() {
        accountRepository.setBalance(1L, new BigDecimal("1000.00"));
        accountRepository.setBalance(2L, new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("演示17a：禁止同一账户向自己转账")
    void sameAccount() {
        assertThatThrownBy(() -> txInvalidService.sameAccount(1L, new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("演示17b：禁止零金额或负金额转账")
    void nonPositiveAmount() {
        assertThatThrownBy(() ->
                txInvalidService.nonPositiveAmount(1L, 2L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("演示17c：余额不足时转账失败")
    void insufficientBalance() {
        assertThatThrownBy(() ->
                txInvalidService.insufficientBalance(1L, 2L, new BigDecimal("1001")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("演示17d：收款账户不存在时转账失败")
    void missingRecipient() {
        assertThatThrownBy(() ->
                txInvalidService.missingRecipient(1L, 999L, new BigDecimal("10")))
                .isInstanceOf(IllegalStateException.class);
    }
}
