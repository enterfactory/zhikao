package com.zhikao.modules.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 随机组卷参数：按「题型 + 数量 + 分值（可选难度）」规则从题库自动抽题。
 */
@Data
public class PaperRandomDTO {

    @NotBlank(message = "试卷名称不能为空")
    private String paperName;

    @NotNull(message = "请选择科目")
    private Long subjectId;

    /** 建议答题时长（分钟） */
    private Integer totalDuration;

    @NotEmpty(message = "请至少配置一条组卷规则")
    private List<Rule> rules;

    /** 组卷规则：某题型抽几道、每道多少分、可选限定难度 */
    @Data
    public static class Rule {

        @NotNull(message = "请选择题型")
        private Integer type;

        @NotNull(message = "请填写抽题数量")
        @Min(value = 1, message = "抽题数量至少为 1")
        private Integer count;

        /** 每题分值，缺省用题目默认分值 */
        private BigDecimal score;

        /** 限定难度 1-5，为空表示不限 */
        private Integer difficulty;
    }
}
