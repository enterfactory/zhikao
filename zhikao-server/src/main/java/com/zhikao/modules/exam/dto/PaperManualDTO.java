package com.zhikao.modules.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 手工组卷参数：由教师勾选具体题目组成试卷。
 */
@Data
public class PaperManualDTO {

    @NotBlank(message = "试卷名称不能为空")
    private String paperName;

    @NotNull(message = "请选择科目")
    private Long subjectId;

    /** 建议答题时长（分钟） */
    private Integer totalDuration;

    @NotEmpty(message = "请至少选择一道题目")
    private List<Item> questions;

    /** 组卷条目：题目 + 本题分值（缺省用题目默认分值） */
    @Data
    public static class Item {

        @NotNull(message = "题目 id 不能为空")
        private Long questionId;

        private BigDecimal score;
    }
}
