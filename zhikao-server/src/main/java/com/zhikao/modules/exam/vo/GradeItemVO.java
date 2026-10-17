package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩明细行（教师按考试查看 / 学生本人成绩共用基础字段）。
 */
@Data
public class GradeItemVO {

    private Long examStudentId;

    private Long studentId;

    private String username;

    private String studentName;

    private String className;

    /** 0待考 1考试中 2已交卷 3已阅卷 */
    private Integer status;

    private String statusName;

    private LocalDateTime enterTime;

    private LocalDateTime submitTime;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    /** 防作弊异常次数 */
    private Integer cheatCount;

    /** 名次（按总分降序；总分相同时名次相同） */
    private Integer rank;
}
