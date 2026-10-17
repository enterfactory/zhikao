package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 主观题批改条目：教师阅卷页面每行所需信息。
 */
@Data
public class MarkItemVO {

    /** exam_answer.id，批改时提交它 */
    private Long answerId;

    private Long studentId;

    private String studentName;

    private Long paperQuestionId;

    /** 题目在试卷中的序号 */
    private Integer sort;

    /** 题干 */
    private String content;

    /** 参考答案（快照） */
    private String standardAnswer;

    /** 本题满分 */
    private BigDecimal fullScore;

    /** 学生作答 */
    private String studentAnswer;

    /** 已打分（未批改时为 null） */
    private BigDecimal score;

    /** 0未批改 1已批改 */
    private Integer marked;
}
