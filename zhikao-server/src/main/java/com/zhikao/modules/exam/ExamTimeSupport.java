package com.zhikao.modules.exam;

import com.zhikao.entity.Exam;
import com.zhikao.entity.ExamStudent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 考试时间计算：把「答题截止时间」的口径收敛到一处，避免各模块各算一套导致不一致。
 *
 * 口径：截止时间 = min(进入时间 + 答题时长, 考试结束时间)。
 * 取 min 是为了防止学生临近结束才进场，答题时间超出考试整体时间窗。
 */
@Component
public class ExamTimeSupport {

    /** 个人答题截止时间 */
    public LocalDateTime deadline(Exam exam, ExamStudent record, LocalDateTime now) {
        LocalDateTime enterTime = record.getEnterTime() == null ? now : record.getEnterTime();
        LocalDateTime personalDeadline = enterTime.plusMinutes(exam.getDuration());
        return personalDeadline.isBefore(exam.getEndTime()) ? personalDeadline : exam.getEndTime();
    }

    /** 剩余答题秒数，永不为负（服务端为准，前端仅做展示倒计时） */
    public long remainingSeconds(Exam exam, ExamStudent record, LocalDateTime now) {
        return Math.max(Duration.between(now, deadline(exam, record, now)).getSeconds(), 0L);
    }

    /** 是否已超过答题截止时间 */
    public boolean isExpired(Exam exam, ExamStudent record, LocalDateTime now) {
        return !now.isBefore(deadline(exam, record, now));
    }
}
