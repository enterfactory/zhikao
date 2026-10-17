package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生本人成绩条目（成绩列表用）。
 */
@Data
public class StudentGradeVO {

    private Integer releaseStatus;

    private Long examId;

    private String examName;

    private String paperName;

    private String subjectName;

    private LocalDateTime submitTime;

    /** 2已交卷 3已阅卷 */
    private Integer status;

    private String statusName;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    /** 试卷总分，便于前端展示「得分/满分」 */
    private BigDecimal paperTotalScore;
}
