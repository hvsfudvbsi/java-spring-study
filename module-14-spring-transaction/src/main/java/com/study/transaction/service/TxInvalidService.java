package com.study.transaction.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 非法事务输入示例：验证失败发生在数据库操作之前。 */
@Service
public class TxInvalidService {

    private final TransferService transferService;

    public TxInvalidService(TransferService transferService) {
        this.transferService = transferService;
    }

    public void sameAccount(Long accountId, BigDecimal amount) {
        transferService.transferSuccess(accountId, accountId, amount);
    }

    public void nonPositiveAmount(Long fromId, Long toId, BigDecimal amount) {
        transferService.transferSuccess(fromId, toId, amount);
    }

    public void insufficientBalance(Long fromId, Long toId, BigDecimal amount) {
        transferService.transferSuccess(fromId, toId, amount);
    }

    public void missingRecipient(Long fromId, Long toId, BigDecimal amount) {
        transferService.transferSuccess(fromId, toId, amount);
    }
}
