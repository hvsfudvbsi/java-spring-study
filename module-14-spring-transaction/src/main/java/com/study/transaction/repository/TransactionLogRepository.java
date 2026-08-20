package com.study.transaction.repository;

import com.study.transaction.model.TransactionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TransactionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(String propagation, String message) {
        jdbcTemplate.update(
                "INSERT INTO transaction_log(propagation, message) VALUES (?, ?)",
                propagation, message);
    }

    public List<TransactionLog> findAll() {
        return jdbcTemplate.query(
                "SELECT id, propagation, message, created_at FROM transaction_log ORDER BY id",
                (rs, rowNum) -> new TransactionLog(
                        rs.getLong("id"),
                        rs.getString("propagation"),
                        rs.getString("message"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM transaction_log");
    }
}
