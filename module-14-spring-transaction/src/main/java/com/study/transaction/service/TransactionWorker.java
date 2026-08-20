package com.study.transaction.service;

import com.study.transaction.repository.TransactionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内层事务 Bean。
 *
 * 它必须独立成 Bean，外层服务调用它时才能经过 Spring AOP 代理，
 * 从而真正切换到相应的传播行为。
 */
@Service
public class TransactionWorker {

    private final TransactionLogRepository logRepository;

    public TransactionWorker(TransactionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /** REQUIRED：加入调用方已有的事务；没有事务时创建新事务。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredInner() {
        logRepository.append("REQUIRED", "inner log - same transaction as outer");
        throw new IllegalStateException("REQUIRED inner failed");
    }

    /** REQUIRES_NEW：挂起外层事务，独立提交自己的日志。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewInner() {
        logRepository.append("REQUIRES_NEW", "inner log - committed independently");
    }

    /** NESTED：在外层事务中建立保存点，异常只回滚保存点之后的操作。 */
    @Transactional(propagation = Propagation.NESTED)
    public void nestedInner() {
        logRepository.append("NESTED", "inner log - rolled back to savepoint");
        throw new IllegalStateException("NESTED inner failed");
    }

    /** SUPPORTS：有事务就加入，没有事务也允许以非事务方式执行。 */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void supportsInner() {
        logRepository.append("SUPPORTS", "inner log - joins transaction if present");
    }

    /** NOT_SUPPORTED：挂起当前事务，以非事务方式执行。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedInner() {
        logRepository.append("NOT_SUPPORTED", "inner log - committed outside outer transaction");
    }

    /** MANDATORY：必须存在调用方事务，否则立即抛出异常。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryInner() {
        logRepository.append("MANDATORY", "inner log - requires an existing transaction");
    }

    /** NEVER：禁止在事务中执行；无事务时允许执行。 */
    @Transactional(propagation = Propagation.NEVER)
    public void neverInner() {
        logRepository.append("NEVER", "inner log - runs without a transaction");
    }
}
