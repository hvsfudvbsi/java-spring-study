package com.study.transaction;

import com.study.transaction.service.IsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class IsolationServiceTest {

    private static final Long ALICE = 1L;

    @Autowired
    private IsolationService isolationService;

    @Autowired
    private com.study.transaction.repository.AccountRepository accountRepository;

    @BeforeEach
    void resetBalances() {
        accountRepository.setBalance(ALICE, new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("演示15a：指定隔离级别 REPEATABLE_READ 读取")
    void readWithIsolation() {
        assertDoesNotThrow(() -> isolationService.readWithIsolation(ALICE));
    }

    @Test
    @DisplayName("演示15b：只读事务 readOnly=true")
    void readOnlyTransaction() {
        assertDoesNotThrow(() -> isolationService.readOnlyTransaction());
    }

    @Test
    @DisplayName("演示15c：事务超时 timeout=3s 正常执行")
    void withTimeout() {
        assertDoesNotThrow(() -> isolationService.withTimeout(ALICE));
    }
}
