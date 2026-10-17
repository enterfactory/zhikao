package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

/**
 * 题库按难度聚合的结果（AI 工具的只读统计）。
 */
@Data
public class QuestionDifficultyCountVO {

    private Integer difficulty;

    private Long total;
}
