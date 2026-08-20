package com.study.transaction.service;

import com.study.transaction.repository.TransactionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 直接调用 TransactionWorker 的每个事务方法。
 *
 * 这些测试验证方法自身的业务操作和异常；事务边界、提交、回滚及保存点
 * 由 TransactionServiceTest 使用真实 Spring 代理和 H2 数据库验证。
 */
class TransactionWorkerUnitTest {

    private TransactionLogRepository logRepository;
    private TransactionWorker transactionWorker;

    @BeforeEach
    void setUp() {
        logRepository = mock(TransactionLogRepository.class);
        transactionWorker = new TransactionWorker(logRepository);
    }

    @Test
    void requiredInnerShouldWriteLogThenThrow() {
        assertThatThrownBy(transactionWorker::requiredInner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REQUIRED inner failed");

        verify(logRepository).append("REQUIRED", "inner log - same transaction as outer");
    }

    @Test
    void requiresNewInnerShouldWriteIndependentLog() {
        transactionWorker.requiresNewInner();

        verify(logRepository).append("REQUIRES_NEW", "inner log - committed independently");
    }

    @Test
    void nestedInnerShouldWriteLogThenThrow() {
        assertThatThrownBy(transactionWorker::nestedInner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NESTED inner failed");

        verify(logRepository).append("NESTED", "inner log - rolled back to savepoint");
    }
}
