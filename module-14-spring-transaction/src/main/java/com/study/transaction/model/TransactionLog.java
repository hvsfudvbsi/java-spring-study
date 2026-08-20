package com.study.transaction.model;

import java.time.LocalDateTime;

/** 事务传播行为示例写入的日志。 */
public record TransactionLog(Long id, String propagation, String message, LocalDateTime createdAt) {
}
