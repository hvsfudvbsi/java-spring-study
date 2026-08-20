package com.study.transaction;

import com.study.transaction.repository.TransactionLogRepository;
import com.study.transaction.service.TransactionPropagationService;
import com.study.transaction.service.TransactionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PropagationServiceTest {

    @Autowired
    private TransactionPropagationService propagationService;

    @Autowired
    private TransactionWorker transactionWorker;

    @Autowired
    private TransactionLogRepository logRepository;

    @BeforeEach
    void clearLogs() {
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("演示4：REQUIRED 内外层共用事务，异常全部回滚")
    void required() {
        assertThrows(IllegalStateException.class, propagationService::requiredRollback);

        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("演示5：REQUIRES_NEW 内层独立提交")
    void requiresNew() {
        assertThrows(IllegalStateException.class, propagationService::requiresNewOuterRollback);

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("inner log - committed independently");
    }

    @Test
    @DisplayName("演示6：NESTED 回滚保存点后外层继续提交")
    void nested() {
        propagationService.nestedOuterContinues();

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("outer log - committed", "outer continued after nested rollback");
    }

    @Test
    @DisplayName("演示7：SUPPORTS 加入外层事务")
    void supports() {
        propagationService.supportsOuterCommit();

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("outer log - committed", "inner log - joins transaction if present");
    }

    @Test
    @DisplayName("演示8：NOT_SUPPORTED 挂起外层事务")
    void notSupported() {
        assertThrows(IllegalStateException.class, propagationService::notSupportedOuterRollback);

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("inner log - committed outside outer transaction");
    }

    @Test
    @DisplayName("演示9：MANDATORY 无事务调用失败，有事务调用成功")
    void mandatory() {
        assertThrows(IllegalTransactionStateException.class, transactionWorker::mandatoryInner);
        propagationService.mandatoryOuterCommit();

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("outer log - committed", "inner log - requires an existing transaction");
    }

    @Test
    @DisplayName("演示10：NEVER 无事务调用成功，有事务调用失败")
    void never() {
        transactionWorker.neverInner();
        assertThrows(IllegalTransactionStateException.class, propagationService::neverOuterRollback);

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("inner log - runs without a transaction");
    }
}
