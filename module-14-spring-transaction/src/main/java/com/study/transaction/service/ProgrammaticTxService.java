package com.study.transaction.service;

import com.study.transaction.repository.TransactionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用 TransactionTemplate 展示编程式事务。 */
@Service
public class ProgrammaticTxService {

    private final TransactionLogRepository logRepository;
    private final TransactionTemplate transactionTemplate;

    public ProgrammaticTxService(TransactionLogRepository logRepository,
                                 PlatformTransactionManager transactionManager) {
        this.logRepository = logRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 回调正常结束，事务提交。 */
    public void executeSuccess() {
        transactionTemplate.executeWithoutResult(status ->
                logRepository.append("PROGRAMMATIC", "programmatic transaction committed"));
    }

    /** 显式标记 rollback-only，回调结束后事务回滚。 */
    public void executeRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            logRepository.append("PROGRAMMATIC", "programmatic transaction rolled back");
            status.setRollbackOnly();
        });
    }

    /** 回调抛运行时异常，TransactionTemplate 自动回滚。 */
    public void executeWithRuntimeException() {
        transactionTemplate.executeWithoutResult(status -> {
            logRepository.append("PROGRAMMATIC", "programmatic exception rolled back");
            throw new IllegalStateException("编程式事务运行时异常");
        });
    }
}
