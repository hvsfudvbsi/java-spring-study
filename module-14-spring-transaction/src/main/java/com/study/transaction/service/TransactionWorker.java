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

    /** REQUIRED：加入调用方已有的事务。 */
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
}
