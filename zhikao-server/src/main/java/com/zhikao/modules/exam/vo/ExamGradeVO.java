package com.zhikao.modules.exam.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 某场考试的成绩统计（教师视角）：概览指标 + 分数分布 + 排名明细。
 */
@Data
public class ExamGradeVO {

    private Long examId;

    private String examName;

    private String paperName;

    private String subjectName;

    private String className;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 试卷总分 */
    private BigDecimal paperTotalScore;

    /** 及格线（试卷总分的 60%，四舍五入两位） */
    private BigDecimal passLine;

    /** 当前班级启用学生数；尚非历史参考名单，见 rosterBasis。 */
    private long shouldCount;

    private String rosterBasis;

    /** 分数统计与排名仅纳入终评，明细仍保留待阅卷记录。 */
    private String scoreBasis;

    private long pendingMarkCount;

    /** 已交卷人数 */
    private long submittedCount;

    /** 已阅卷人数 */
    private long markedCount;

    /** 参与统计的人数（终评且有分数） */
    private int participantCount;

    private BigDecimal avgScore;

    private BigDecimal maxScore;

    private BigDecimal minScore;

    /** 及格率（百分比，保留两位） */
    private BigDecimal passRate;

    /** 分数分布（按 10 分一段） */
    private List<ScoreBucket> distribution = new ArrayList<>();

    /** 成绩明细（按总分降序，含名次） */
    private List<GradeItemVO> items = new ArrayList<>();

    /** 分数区间统计 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBucket {

        private String label;

        private int count;
    }
}
