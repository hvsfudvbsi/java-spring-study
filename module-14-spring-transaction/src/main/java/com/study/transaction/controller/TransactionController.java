package com.study.transaction.controller;

import com.study.transaction.model.Account;
import com.study.transaction.model.TransactionLog;
import com.study.transaction.repository.AccountRepository;
import com.study.transaction.repository.TransactionLogRepository;
import com.study.transaction.service.AccountService;
import com.study.transaction.service.TransactionPropagationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TransactionLogRepository logRepository;
    private final TransactionPropagationService propagationService;

    public TransactionController(AccountService accountService,
                                 AccountRepository accountRepository,
                                 TransactionLogRepository logRepository,
                                 TransactionPropagationService propagationService) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.logRepository = logRepository;
        this.propagationService = propagationService;
    }

    @PostMapping("/transfer")
    public Map<String, String> transfer(@RequestParam Long fromId,
                                        @RequestParam Long toId,
                                        @RequestParam BigDecimal amount) {
        accountService.transfer(fromId, toId, amount);
        return Map.of("message", "转账成功");
    }

    @GetMapping("/accounts")
    public List<Account> accounts() {
        return accountRepository.findAll();
    }

    @PostMapping("/propagation/required")
    public Map<String, String> required() {
        propagationService.requiredRollback();
        return Map.of("message", "不会执行到这里：REQUIRED 内层异常会回滚整个事务");
    }

    @PostMapping("/propagation/requires-new")
    public Map<String, String> requiresNew() {
        propagationService.requiresNewOuterRollback();
        return Map.of("message", "不会执行到这里：外层事务会回滚");
    }

    @PostMapping("/propagation/nested")
    public Map<String, String> nested() {
        propagationService.nestedOuterContinues();
        return Map.of("message", "NESTED 内层回滚后，外层仍然提交");
    }

    @PostMapping("/propagation/supports")
    public Map<String, String> supports() {
        propagationService.supportsOuterCommit();
        return Map.of("message", "SUPPORTS 在有外层事务时加入外层事务");
    }

    @PostMapping("/propagation/not-supported")
    public Map<String, String> notSupported() {
        propagationService.notSupportedOuterRollback();
        return Map.of("message", "不会执行到这里：NOT_SUPPORTED 外层事务会回滚");
    }

    @PostMapping("/propagation/mandatory")
    public Map<String, String> mandatory() {
        propagationService.mandatoryOuterCommit();
        return Map.of("message", "MANDATORY 在外层事务存在时正常执行");
    }

    @PostMapping("/propagation/never")
    public Map<String, String> never() {
        propagationService.neverOuterRollback();
        return Map.of("message", "不会执行到这里：NEVER 禁止在事务中执行");
    }

    @GetMapping("/logs")
    public List<TransactionLog> logs() {
        return logRepository.findAll();
    }
}
