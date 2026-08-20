package com.study.transaction.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 多线程事务示例：每个线程通过 TransferService 代理开启独立事务。 */
@Service
public class MultiThreadTxService {

    private final TransferService transferService;

    public MultiThreadTxService(TransferService transferService) {
        this.transferService = transferService;
    }

    /** 并发执行多次转账，所有任务完成后才返回。 */
    public void transferConcurrently(Long fromId, Long toId, BigDecimal amount, int taskCount)
            throws Exception {
        if (taskCount <= 0) {
            throw new IllegalArgumentException("任务数量必须大于 0");
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(taskCount, 8));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() ->
                        transferService.transferSuccess(fromId, toId, amount)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
    }
}
