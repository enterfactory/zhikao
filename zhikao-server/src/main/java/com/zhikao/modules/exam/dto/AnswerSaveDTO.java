package com.zhikao.modules.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 答题保存参数：单题保存，前端在切题 / 定时时自动调用。
 */
@Data
public class AnswerSaveDTO {

    @NotNull(message = "题目不能为空")
    private Long paperQuestionId;

    /** 学生作答内容；传空字符串表示清空该题作答 */
    @jakarta.validation.constraints.Size(max = 20000)
    private String answer;

    /** Version received on entry, incremented after each acknowledged save. */
    @NotNull(message = "请携带作答版本")
    @jakarta.validation.constraints.PositiveOrZero
    private Integer expectedVersion;
}
