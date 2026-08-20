package com.study.transaction;

import com.study.transaction.model.TransactionLog;
import com.study.transaction.repository.AccountRepository;
import com.study.transaction.repository.TransactionLogRepository;
import com.study.transaction.service.AccountService;
import com.study.transaction.service.TransactionPropagationService;
import com.study.transaction.service.TransactionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

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

    @Autowired
    private TransactionWorker transactionWorker;

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

    @Test
    void supportsShouldJoinOuterTransaction() {
        propagationService.supportsOuterCommit();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly(
                        "outer log - committed",
                        "inner log - joins transaction if present");
    }

    @Test
    void notSupportedShouldCommitInnerLogAfterOuterRollback() {
        assertThatThrownBy(propagationService::notSupportedOuterRollback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NOT_SUPPORTED outer transaction failed");

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly("inner log - committed outside outer transaction");
    }

    @Test
    void mandatoryShouldRejectCallWithoutExistingTransaction() {
        assertThatThrownBy(transactionWorker::mandatoryInner)
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    void mandatoryShouldJoinExistingOuterTransaction() {
        propagationService.mandatoryOuterCommit();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly(
                        "outer log - committed",
                        "inner log - requires an existing transaction");
    }

    @Test
    void neverShouldRunWithoutExistingTransaction() {
        transactionWorker.neverInner();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly("inner log - runs without a transaction");
    }

    @Test
    void neverShouldRejectCallFromTransactionalOuterMethod() {
        assertThatThrownBy(propagationService::neverOuterRollback)
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(logRepository.findAll()).isEmpty();
    }
}
