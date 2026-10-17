package com.zhikao.modules.exam;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.*;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import com.zhikao.modules.notification.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class ExamReleaseService {
    private final ExamMapper exams;
    private final ExamStudentMapper attempts;
    private final DataScope scope;
    private final NotificationService notifications;

    @Transactional(rollbackFor=Exception.class)
    public void release(Long id,int level) {
        if(level<1 || level>2)throw new BusinessException("无效发布级别");
        Exam exam = exams.lockById(id);
        if (exam == null) throw new BusinessException("考试不存在");
        scope.requireOwner(exam.getCreateBy(), "考试");
        int current = exam.getReleaseStatus() == null ? 0 : exam.getReleaseStatus();
        if (current >= level) return;
        if (!Integer.valueOf(1).equals(exam.getPublished()) || LocalDateTime.now().isBefore(exam.getEndTime())) {
            throw new BusinessException("全场考试结束后才能发布");
        }
        if (attempts.selectCount(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id).lt(ExamStudent::getStatus, 3)) > 0) {
            throw new BusinessException("请先完成收卷和评分核对");
        }
        exam.setReleaseStatus(level);
        exams.updateById(exam);
        var students = attempts.selectList(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id)).stream().map(ExamStudent::getStudentId).toList();
        notifications.send(students, NotificationTypeEnum.GRADE_PUBLISHED,
                (level == 2 ? "解析与练习已开放：" : "成绩已公布：") + exam.getExamName(),
                level == 2 ? "可以查看试卷解析，并在练习空间巩固相关知识点。" : "可以前往学习记录查询本人成绩。",
                "exam", id);
    }
}
