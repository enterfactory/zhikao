package com.zhikao.modules.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 题目新增 / 修改参数。
 * id 为空表示新增，不为空表示修改。
 */
@Data
public class QuestionSaveDTO {

    private Long id;

    @NotNull(message = "请选择科目")
    private Long subjectId;

    @NotNull(message = "请选择题型")
    private Integer type;

    @NotBlank(message = "题干不能为空")
    private String content;

    /** 选项：key 为 A/B/C/D，value 为选项内容；判断题与填空/简答可为空 */
    private Map<String, String> options;

    @NotBlank(message = "标准答案不能为空")
    private String answer;

    /** 解析（可选） */
    private String analysis;

    /** 难度 1-5 */
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 5, message = "难度最大为 5")
    private Integer difficulty;

    /** 知识点（可选） */
    private String knowledgePoint;

    /** 分值 */
    private BigDecimal score;
}
