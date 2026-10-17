package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

/**
 * 题库按题型聚合的结果（AI 工具的只读统计）。
 */
@Data
public class QuestionTypeCountVO {

    private Integer type;

    private Long total;
}
