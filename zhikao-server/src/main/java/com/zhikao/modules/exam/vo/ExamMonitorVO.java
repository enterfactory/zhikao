package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 考试实时监控：教师看某场考试的参与情况与得分概览。
 */
@Data
public class ExamMonitorVO {

    private Long examId;

    private String examName;

    /** 应考人数（发布时冻结；历史未冻结仅为实际参与者） */
    private long shouldCount;
    private String rosterBasis;

    /** 已进入考试人数 */
    private long enteredCount;

    /** 已交卷人数 */
    private long submittedCount;

    /** 已阅卷人数（含全客观题自动阅卷完成） */
    private long markedCount;

    /** 平均分（已出分的学生） */
    private BigDecimal avgScore;

    /** 最高分 */
    private BigDecimal maxScore;

    /** 最低分 */
    private BigDecimal minScore;
}
