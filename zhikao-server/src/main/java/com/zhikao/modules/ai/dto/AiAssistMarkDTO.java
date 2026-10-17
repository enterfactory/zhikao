package com.zhikao.modules.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 主观题辅助阅卷请求。
 */
@Data
public class AiAssistMarkDTO {

    /** 本题满分 */
    @NotNull(message = "缺少本题满分")
    private BigDecimal fullScore;

    /** 题干 */
    private String question;

    /** 参考答案 */
    private String standardAnswer;

    /** 学生作答 */
    private String studentAnswer;
}
