package com.study.transaction.repository;

import com.study.transaction.model.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按条件扣款，返回 0 表示账户不存在或余额不足。 */
    public int withdraw(Long accountId, BigDecimal amount) {
        return jdbcTemplate.update(
                "UPDATE bank_account SET balance = balance - ? WHERE id = ? AND balance >= ?",
                amount, accountId, amount);
    }

    public int deposit(Long accountId, BigDecimal amount) {
        return jdbcTemplate.update(
                "UPDATE bank_account SET balance = balance + ? WHERE id = ?",
                amount, accountId);
    }

    public List<Account> findAll() {
        return jdbcTemplate.query(
                "SELECT id, owner, balance FROM bank_account ORDER BY id",
                (rs, rowNum) -> new Account(
                        rs.getLong("id"),
                        rs.getString("owner"),
                        rs.getBigDecimal("balance")));
    }

    public void resetBalances() {
        jdbcTemplate.update("UPDATE bank_account SET balance = CASE id WHEN 1 THEN 100.00 ELSE 50.00 END");
    }
}
