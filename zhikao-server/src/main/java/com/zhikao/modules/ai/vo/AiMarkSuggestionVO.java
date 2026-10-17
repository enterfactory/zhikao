package com.zhikao.modules.ai.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 辅助阅卷建议。
 * 说明：仅作参考，最终分数始终由教师确认，系统不会用建议分直接覆盖成绩。
 */
@Data
public class AiMarkSuggestionVO {

    /** 建议分数；模型未按约定格式返回时为 null */
    private BigDecimal suggestScore;

    /** 判定理由 */
    private String reason;

    /** 模型原始返回，便于教师核对 */
    private String raw;

    /** 是否由大模型生成 */
    private boolean aiGenerated;
}
