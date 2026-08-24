package com.study.transaction.service;

import com.study.transaction.repository.TransactionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("REQUIRED 内层：写日志后抛异常（事务边界由集成测试验证）")
    void requiredInnerShouldWriteLogThenThrow() {
        assertThatThrownBy(transactionWorker::requiredInner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REQUIRED inner failed");

        verify(logRepository).append("REQUIRED", "inner log - same transaction as outer");
    }

    @Test
    @DisplayName("REQUIRES_NEW 内层：写独立日志（事务边界由集成测试验证）")
    void requiresNewInnerShouldWriteIndependentLog() {
        transactionWorker.requiresNewInner();

        verify(logRepository).append("REQUIRES_NEW", "inner log - committed independently");
    }

    @Test
    @DisplayName("NESTED 内层：写日志后抛异常（保存点由集成测试验证）")
    void nestedInnerShouldWriteLogThenThrow() {
        assertThatThrownBy(transactionWorker::nestedInner)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NESTED inner failed");

        verify(logRepository).append("NESTED", "inner log - rolled back to savepoint");
    }

    @Test
    @DisplayName("SUPPORTS 内层：写日志（事务边界由集成测试验证）")
    void supportsInnerShouldWriteLog() {
        transactionWorker.supportsInner();

        verify(logRepository).append("SUPPORTS", "inner log - joins transaction if present");
    }

    @Test
    @DisplayName("NOT_SUPPORTED 内层：写日志（挂起语义由集成测试验证）")
    void notSupportedInnerShouldWriteLog() {
        transactionWorker.notSupportedInner();

        verify(logRepository).append("NOT_SUPPORTED", "inner log - committed outside outer transaction");
    }

    @Test
    @DisplayName("MANDATORY 内层直接调用：写日志（无事务时拒绝由集成测试验证）")
    void mandatoryInnerShouldWriteLogWhenMethodIsCalledDirectly() {
        transactionWorker.mandatoryInner();

        verify(logRepository).append("MANDATORY", "inner log - requires an existing transaction");
    }

    @Test
    @DisplayName("NEVER 内层直接调用：写日志（有事务时拒绝由集成测试验证）")
    void neverInnerShouldWriteLogWhenMethodIsCalledDirectly() {
        transactionWorker.neverInner();

        verify(logRepository).append("NEVER", "inner log - runs without a transaction");
    }
}
