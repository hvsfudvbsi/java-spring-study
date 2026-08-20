package com.study.transaction;

import com.study.transaction.model.TransactionLog;
import com.study.transaction.repository.AccountRepository;
import com.study.transaction.repository.TransactionLogRepository;
import com.study.transaction.service.AccountService;
import com.study.transaction.service.TransactionPropagationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransactionServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionLogRepository logRepository;

    @Autowired
    private TransactionPropagationService propagationService;

    @BeforeEach
    void resetDatabase() {
        logRepository.deleteAll();
        accountRepository.resetBalances();
    }

    @Test
    void transferShouldCommitBothAccountUpdates() {
        accountService.transfer(1L, 2L, new BigDecimal("30.00"));

        assertThat(accountRepository.findAll()).extracting("balance")
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("80.00"));
    }

    @Test
    void transferShouldRollbackWhenRecipientDoesNotExist() {
        assertThatThrownBy(() -> accountService.transfer(1L, 999L, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("转入账户不存在");

        assertThat(accountRepository.findAll()).extracting("balance")
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("50.00"));
    }

    @Test
    void requiredShouldRollbackOuterAndInnerWorkTogether() {
        assertThatThrownBy(propagationService::requiredRollback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REQUIRED inner failed");

        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    void requiresNewShouldKeepInnerCommitAfterOuterRollback() {
        assertThatThrownBy(propagationService::requiresNewOuterRollback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outer transaction failed after inner commit");

        List<TransactionLog> logs = logRepository.findAll();
        assertThat(logs).singleElement()
                .satisfies(log -> {
                    assertThat(log.propagation()).isEqualTo("REQUIRES_NEW");
                    assertThat(log.message()).isEqualTo("inner log - committed independently");
                });
    }

    @Test
    void nestedShouldRollbackOnlyInnerWorkAndCommitOuterWork() {
        propagationService.nestedOuterContinues();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly(
                        "outer log - committed",
                        "outer continued after nested rollback");
    }
}
