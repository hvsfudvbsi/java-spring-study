package com.study.advanced.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时任务：@Scheduled（需 @EnableScheduling 开启）
 *
 * 三种调度方式：
 *   @Scheduled(fixedRate = 5000)        每 5 秒执行（不等待上次完成）
 *   @Scheduled(fixedDelay = 5000)       上次执行完 5 秒后再执行
 *   @Scheduled(cron = "0 0 2 * * ?")    Cron 表达式（每天凌晨 2 点）
 *
 * Cron 格式（6 位或 7 位）：秒 分 时 日 月 周
 *   0 0 2 * * ?        每天 02:00:00
 *   0 0/5 * * * ?      每 5 分钟（Cron 步进写法，注意注释里不能写星号斜杠）
 *   0 0 9-18 * * MON-FRI  工作日 9 点到 18 点整点
 *
 * 生产建议：
 *   - 单机用 @Scheduled，分布式环境用 xxl-job / Quartz 集群
 *   - 定时任务要幂等（重复执行结果一致）
 *   - 默认单线程执行，任务耗时会阻塞下一个任务
 */
@Component
public class ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);

    /** 每 5 秒执行一次（学习用；生产环境频率要克制） */
    @Scheduled(fixedRate = 5000)
    public void heartbeat() {
        log.info("[定时任务] 心跳检测: {}", LocalDateTime.now());
    }

    /** Cron 示例：每分钟的第 30 秒执行 */
    @Scheduled(cron = "30 * * * * ?")
    public void minuteTask() {
        log.info("[定时任务] 每分钟第 30 秒: {}", LocalDateTime.now());
    }
}
