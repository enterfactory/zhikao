package com.zhikao.modules.workflow;
import com.zhikao.entity.Exam;
import com.zhikao.modules.exam.AnswerVisibilityPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnswerVisibilityPolicyTest {
    @Test void wholeExamMustEndAndTeacherMustPublishAnalysis() {
        LocalDateTime end=LocalDateTime.of(2026,10,15,12,0);
        Exam exam=new Exam();exam.setEndTime(end);exam.setReleaseStatus(2);
        assertFalse(AnswerVisibilityPolicy.canReview(exam,end.minusNanos(1)));
        assertTrue(AnswerVisibilityPolicy.canReview(exam,end));
        exam.setReleaseStatus(1);assertFalse(AnswerVisibilityPolicy.canReview(exam,end.plusDays(1)));
        exam.setReleaseStatus(null);assertFalse(AnswerVisibilityPolicy.canReview(exam,end.plusDays(1)));
        assertFalse(AnswerVisibilityPolicy.canReview(null,end));
    }
}
