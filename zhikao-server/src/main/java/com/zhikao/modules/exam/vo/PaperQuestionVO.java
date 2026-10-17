package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 试卷题目（快照）返回对象：教师预览试卷 / 学生取卷共用。
 */
@Data
public class PaperQuestionVO {

    /** paper_question.id，学生答题时按它提交答案 */
    private Long id;

    /** 源题目 id，便于溯源（学生取卷时不下发） */
    private Long questionId;

    /** 题目顺序，从 1 开始 */
    private Integer sort;

    private Integer type;

    private String typeName;

    /** 是否客观题 */
    private Boolean objective;

    private String content;

    /** 选项 A/B/C/D -> 内容（有序） */
    private Map<String, String> options = new LinkedHashMap<>();

    /** 本题分值 */
    private BigDecimal score;

    private Integer difficulty;

    private String knowledgePoint;

    /** 标准答案：仅教师预览/阅卷时下发，学生取卷时置空 */
    private String answer;

    /** 解析：仅阅卷后或教师预览时下发 */
    private String analysis;
}
