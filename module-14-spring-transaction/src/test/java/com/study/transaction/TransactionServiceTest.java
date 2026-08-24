package com.study.transaction;

import com.study.transaction.model.TransactionLog;
import com.study.transaction.repository.AccountRepository;
import com.study.transaction.repository.TransactionLogRepository;
import com.study.transaction.service.AccountService;
import com.study.transaction.service.TransactionPropagationService;
import com.study.transaction.service.TransactionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("转账成功：两个账户余额同时更新并提交（100→70 与 50→80）")
    void transferShouldCommitBothAccountUpdates() {
        accountService.transfer(1L, 2L, new BigDecimal("30.00"));

        assertThat(accountRepository.findAll()).extracting("balance")
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("转账目标不存在：抛异常且余额不变（事务回滚）")
    void transferShouldRollbackWhenRecipientDoesNotExist() {
        assertThatThrownBy(() -> accountService.transfer(1L, 999L, new BigDecimal("30.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("转入账户不存在");

        assertThat(accountRepository.findAll()).extracting("balance")
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("REQUIRED：内层异常使外层一并回滚（同一事务）")
    void requiredShouldRollbackOuterAndInnerWorkTogether() {
        assertThatThrownBy(propagationService::requiredRollback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REQUIRED inner failed");

        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("REQUIRES_NEW：外层回滚但内层独立事务已提交保留")
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
    @DisplayName("NESTED：内层保存点回滚，外层继续并提交")
    void nestedShouldRollbackOnlyInnerWorkAndCommitOuterWork() {
        propagationService.nestedOuterContinues();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly(
                        "outer log - committed",
                        "outer continued after nested rollback");
    }

    @Test
    @DisplayName("SUPPORTS：有外层事务则加入，无则独立执行（此处加入并提交）")
    void supportsShouldJoinOuterTransaction() {
        propagationService.supportsOuterCommit();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly(
                        "outer log - committed",
                        "inner log - joins transaction if present");
    }

    @Test
    @DisplayName("NOT_SUPPORTED：内层挂起外层事务独立提交，外层随后回滚")
    void notSupportedShouldCommitInnerLogAfterOuterRollback() {
        assertThatThrownBy(propagationService::notSupportedOuterRollback)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NOT_SUPPORTED outer transaction failed");

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly("inner log - committed outside outer transaction");
    }

    @Test
    @DisplayName("MANDATORY：无外层事务时直接拒绝（IllegalTransactionStateException）")
    void mandatoryShouldRejectCallWithoutExistingTransaction() {
        assertThatThrownBy(transactionWorker::mandatoryInner)
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("MANDATORY：有外层事务时加入并提交")
    void mandatoryShouldJoinExistingOuterTransaction() {
        propagationService.mandatoryOuterCommit();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly(
                        "outer log - committed",
                        "inner log - requires an existing transaction");
    }

    @Test
    @DisplayName("NEVER：无外层事务时正常执行")
    void neverShouldRunWithoutExistingTransaction() {
        transactionWorker.neverInner();

        assertThat(logRepository.findAll()).extracting(TransactionLog::message)
                .containsExactly("inner log - runs without a transaction");
    }

    @Test
    @DisplayName("NEVER：外层有事务时拒绝（IllegalTransactionStateException）")
    void neverShouldRejectCallFromTransactionalOuterMethod() {
        assertThatThrownBy(propagationService::neverOuterRollback)
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(logRepository.findAll()).isEmpty();
    }
}
