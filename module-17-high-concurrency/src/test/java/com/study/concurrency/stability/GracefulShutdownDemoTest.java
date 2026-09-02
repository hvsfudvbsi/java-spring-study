package com.study.concurrency.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GracefulShutdownDemoTest {

    @Test
    @DisplayName("shutdown(): 拒绝新任务但排空队列，5 个提交的任务全部执行完")
    void shutdownDrainsQueue() throws InterruptedException {
        assertEquals(5, GracefulShutdownDemo.shutdownDrainsQueued(5, 40),
                "shutdown() 后排队任务应继续执行，全部完成");
    }

    @Test
    @DisplayName("shutdownNow(): 返回 4 个未执行的排队任务，正在跑的任务收到中断")
    void shutdownNowReturnsPendingAndInterrupts() throws InterruptedException {
        var result = GracefulShutdownDemo.shutdownNowInterrupts(5, 20);
        assertEquals(4, result.pendingCount(), "4 个排队任务被原样退回");
        assertEquals(1, result.interruptedRunCount(), "正在跑的任务收到了 interrupt");
    }
}