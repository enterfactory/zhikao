package com.zhikao.modules.exam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时自动收卷定时任务。
 *
 * <p><b>为什么必须有它：</b>倒计时在前端跑，学生一旦关掉浏览器或断网，
 * 前端的「时间到 → 自动交卷」就永远不会发生。没有服务端收卷时，
 * 这场考试在监考眼里永远是「XX 人考试中」，直到有人手工改库。
 *
 * <p><b>为什么用数据库状态而不是内存计时器：</b>内存计时器在进程重启后全部丢失，
 * 而「谁还没交卷」是已经落在 exam_student 里的既有事实，扫表即可，重启天然可恢复。
 *
 * <p>收卷口径与前端倒计时同源（min(进入时间 + 时长, 考试结束时间)），
 * 避免出现「页面显示还有 2 分钟、后台已经把卷收了」这种对不上的情况。
 */
@Component
public class ExamTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(ExamTimeoutTask.class);

    private final AnswerService answerService;

    /** 单次扫描上限：一次处理太多会拖成长事务，剩下的留给下一轮 */
    private final int limit;

    public ExamTimeoutTask(AnswerService answerService,
                           @Value("${zhikao.exam.timeout-scan-limit:200}") int limit) {
        this.answerService = answerService;
        this.limit = limit;
    }

    /**
     * 每分钟扫一次。用 fixedDelay 而不是 fixedRate：上一轮没跑完就不开下一轮，
     * 避免堆积时多个扫描批次叠在一起抢同一批记录。
     */
    @Scheduled(initialDelayString = "${zhikao.exam.timeout-scan-initial-delay-ms:30000}",
            fixedDelayString = "${zhikao.exam.timeout-scan-delay-ms:60000}")
    public void run() {
        try {
            int count = answerService.autoSubmitExpired(limit);
            if (count > 0) {
                log.info("超时自动收卷 {} 份", count);
            }
        } catch (Exception e) {
            // 定时任务抛异常会中断后续调度周期，这里必须兜住
            log.warn("超时收卷任务执行失败：{}", e.getMessage());
        }
    }
}
