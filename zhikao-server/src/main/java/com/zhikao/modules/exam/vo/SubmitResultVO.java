package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 交卷结果：自动判分后的得分情况。
 */
@Data
public class SubmitResultVO {

    /** 客观题得分 */
    private BigDecimal objectiveScore;

    /** 主观题得分（此时通常为 0，教师批改后累计） */
    private BigDecimal subjectiveScore;

    /** 当前总分 */
    private BigDecimal totalScore;

    /** 试卷总分，便于前端展示「得分/满分」 */
    private BigDecimal paperTotalScore;

    /** 待教师批改的主观题数量，为 0 表示已直接阅卷完成 */
    private int unmarkedCount;

    /** 交卷后本人状态：2已交卷 3已阅卷 */
    private Integer status;

    private String statusName;

    /**
     * 交卷方式：1手动 2超时自动收卷 3作弊强制收卷。
     * 学生需要知道自己是不是被系统收的卷——否则「我明明没点交卷」会变成一个无法解释的问题。
     */
    private Integer submitType;

    private String submitTypeName;

    /** 提示语（如「已交卷，等待教师批改主观题」） */
    private String message;
}
