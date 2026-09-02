package com.study.concurrency.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BackpressureDemoTest {

    @Test
    @DisplayName("有界队列背压: 生产 10 条消费 30ms/条，整个过程正常结束且最大积压不超过容量 2")
    void boundedQueueKeepsMemoryBounded() throws InterruptedException {
        // 直接跑演示方法：内部已断言最大积压 ≤ 2；这里验证没有异常且正常退出
        BackpressureDemo.boundedQueueDemo();
    }

    @Test
    @DisplayName("信号量背压: 在飞上限 2 时执行 30 个任务，任务一个不丢")
    void semaphoreBackpressureRunsAllTasks() throws InterruptedException {
        int executed = BackpressureDemo.semaphoreBackpressure(2, 30);
        assertEquals(30, executed, "限速但不丢任务");
    }
}