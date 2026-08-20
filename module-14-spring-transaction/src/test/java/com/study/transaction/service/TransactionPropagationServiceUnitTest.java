package com.study.transaction.service;

import com.study.transaction.repository.TransactionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 传播服务纯单元测试：验证调用编排；REQUIRED/REQUIRES_NEW/NESTED 的真实事务语义由集成测试验证。
 */
class TransactionPropagationServiceUnitTest {

    private TransactionLogRepository logRepository;
    private TransactionWorker transactionWorker;
    private TransactionPropagationService propagationService;

    @BeforeEach
    void setUp() {
        logRepository = mock(TransactionLogRepository.class);
        transactionWorker = mock(TransactionWorker.class);
        propagationService = new TransactionPropagationService(logRepository, transactionWorker);
    }

    @Test
    void requiredShouldWriteOuterLogAndCallWorker() {
        propagationService.requiredRollback();

        verify(logRepository).append("REQUIRED", "outer log - rolled back with inner");
        verify(transactionWorker).requiredInner();
    }

    @Test
    void requiresNewShouldWriteOuterLogAndPropagateOuterFailure() {
        assertThatThrownBy(() -> propagationService.requiresNewOuterRollback())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outer transaction failed after inner commit");

        verify(logRepository).append("REQUIRES_NEW", "outer log - rolled back later");
        verify(transactionWorker).requiresNewInner();
    }

    @Test
    void nestedShouldContinueAfterWorkerFailure() {
        org.mockito.Mockito.doThrow(new IllegalStateException("nested failed"))
                .when(transactionWorker).nestedInner();

        propagationService.nestedOuterContinues();

        verify(logRepository).append("NESTED", "outer log - committed");
        verify(logRepository).append("NESTED", "outer continued after nested rollback");
    }
}
