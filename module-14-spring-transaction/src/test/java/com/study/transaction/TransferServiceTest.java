package com.study.transaction;

import com.study.transaction.model.Account;
import com.study.transaction.repository.AccountRepository;
import com.study.transaction.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class TransferServiceTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void resetBalances() {
        accountRepository.setBalance(ALICE, new BigDecimal("1000.00"));
        accountRepository.setBalance(BOB, new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("演示1：正常转账成功，两步 SQL 同事务提交")
    void transferSuccess() {
        transferService.transferSuccess(ALICE, BOB, new BigDecimal("100"));

        assertBalance(ALICE, "900.00");
        assertBalance(BOB, "1100.00");
    }

    @Test
    @DisplayName("演示2：运行时异常自动回滚，余额不变")
    void transferWithRuntimeException() {
        assertThrows(IllegalStateException.class, () ->
                transferService.transferWithRuntimeException(ALICE, BOB, new BigDecimal("50")));

        assertBalance(ALICE, "1000.00");
        assertBalance(BOB, "1000.00");
    }

    @Test
    @DisplayName("演示3：受检异常默认不回滚，扣款生效")
    void transferWithCheckedException() {
        assertThrows(Exception.class, () ->
                transferService.transferWithCheckedException(ALICE, BOB, new BigDecimal("50")));

        // 默认只对 RuntimeException 和 Error 回滚，因此扣款已经生效，入账尚未执行。
        assertBalance(ALICE, "950.00");
        assertBalance(BOB, "1000.00");
    }

    private void assertBalance(Long accountId, String expectedBalance) {
        Account account = accountRepository.findById(accountId);
        assertThat(account.balance()).isEqualByComparingTo(expectedBalance);
    }
}
