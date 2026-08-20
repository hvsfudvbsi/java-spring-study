package com.study.transaction.model;

import java.math.BigDecimal;

/** 账户只读模型，用于展示转账事务执行后的结果。 */
public record Account(Long id, String owner, BigDecimal balance) {
}
