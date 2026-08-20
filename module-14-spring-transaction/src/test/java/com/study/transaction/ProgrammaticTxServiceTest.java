package com.study.transaction;

import com.study.transaction.repository.TransactionLogRepository;
import com.study.transaction.service.ProgrammaticTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProgrammaticTxServiceTest {

    @Autowired
    private ProgrammaticTxService programmaticTxService;

    @Autowired
    private TransactionLogRepository logRepository;

    @BeforeEach
    void clearLogs() {
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("演示10a：TransactionTemplate 正常执行并提交")
    void executeSuccess() {
        programmaticTxService.executeSuccess();

        assertThat(logRepository.findAll()).extracting("message")
                .containsExactly("programmatic transaction committed");
    }

    @Test
    @DisplayName("演示10b：显式设置 rollbackOnly 后回滚")
    void executeRollback() {
        programmaticTxService.executeRollback();

        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("演示10c：编程式事务遇到运行时异常自动回滚")
    void executeWithRuntimeException() {
        assertThrows(IllegalStateException.class,
                () -> programmaticTxService.executeWithRuntimeException());

        assertThat(logRepository.findAll()).isEmpty();
    }
}
