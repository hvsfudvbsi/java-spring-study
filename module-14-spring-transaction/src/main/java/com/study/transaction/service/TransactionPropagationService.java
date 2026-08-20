package com.study.transaction.service;

import com.study.transaction.repository.TransactionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 外层事务服务：通过调用独立的 TransactionWorker Bean 展示传播行为。 */
@Service
public class TransactionPropagationService {

    private final TransactionLogRepository logRepository;
    private final TransactionWorker transactionWorker;

    public TransactionPropagationService(TransactionLogRepository logRepository,
                                         TransactionWorker transactionWorker) {
        this.logRepository = logRepository;
        this.transactionWorker = transactionWorker;
    }

    /**
     * REQUIRED 共用一个事务：内层异常向外抛出，外层日志也会一起回滚。
     */
    @Transactional
    public void requiredRollback() {
        logRepository.append("REQUIRED", "outer log - rolled back with inner");
        transactionWorker.requiredInner();
    }

    /**
     * REQUIRES_NEW 内层先独立提交；之后外层失败，只会回滚外层日志。
     */
    @Transactional
    public void requiresNewOuterRollback() {
        logRepository.append("REQUIRES_NEW", "outer log - rolled back later");
        transactionWorker.requiresNewInner();
        throw new IllegalStateException("outer transaction failed after inner commit");
    }

    /**
     * NESTED 内层回滚到保存点，外层捕获异常后仍然可以继续执行并提交。
     */
    @Transactional
    public void nestedOuterContinues() {
        logRepository.append("NESTED", "outer log - committed");
        try {
            transactionWorker.nestedInner();
        } catch (IllegalStateException exception) {
            logRepository.append("NESTED", "outer continued after nested rollback");
        }
    }
}
