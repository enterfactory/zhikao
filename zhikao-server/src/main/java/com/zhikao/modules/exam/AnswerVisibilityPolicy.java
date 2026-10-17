package com.zhikao.modules.exam;

import com.zhikao.entity.Exam;
import java.time.LocalDateTime;

public final class AnswerVisibilityPolicy {
    private AnswerVisibilityPolicy() {}
    public static boolean canSeeScore(Exam exam, LocalDateTime now) {
        return exam != null && exam.getReleaseStatus() != null && exam.getReleaseStatus() >= 1
                && exam.getEndTime() != null && !now.isBefore(exam.getEndTime());
    }
    public static boolean canReview(Exam exam, LocalDateTime now) {
        return exam != null && Integer.valueOf(2).equals(exam.getReleaseStatus())
                && exam.getEndTime() != null && !now.isBefore(exam.getEndTime());
    }
}
