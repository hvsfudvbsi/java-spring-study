package com.study.transaction;

import com.study.transaction.model.Account;
import com.study.transaction.repository.AccountRepository;
import com.study.transaction.service.MultiThreadTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MultiThreadTxServiceTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    @Autowired
    private MultiThreadTxService multiThreadTxService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void resetBalances() {
        accountRepository.setBalance(ALICE, new BigDecimal("1000.00"));
        accountRepository.setBalance(BOB, new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("演示16：多个线程分别开启事务并完成并发转账")
    void concurrentTransfer() throws Exception {
        multiThreadTxService.transferConcurrently(ALICE, BOB, new BigDecimal("10"), 10);

        assertBalance(ALICE, "900.00");
        assertBalance(BOB, "1100.00");
    }

    private void assertBalance(Long accountId, String expectedBalance) {
        Account account = accountRepository.findById(accountId);
        assertThat(account.balance()).isEqualByComparingTo(expectedBalance);
    }
}
