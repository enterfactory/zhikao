package com.zhikao.modules.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能出题请求。
 */
@Data
public class AiGenQuestionDTO {

    /** 科目 id：带上它，返回的草稿就能直接一键入库，不需要前端再按名称反查 */
    private Long subjectId;

    /** 科目名称（用于给模型提供语境，便于与题库保持一致） */
    private String subjectName;

    /** 题型：1单选 2多选 3判断 4填空 5简答 */
    @NotNull(message = "请选择题型")
    private Integer type;

    /** 知识点 / 考察范围 */
    private String knowledgePoint;

    /** 生成数量 */
    @Min(value = 1, message = "至少生成 1 道")
    @Max(value = 10, message = "单次最多生成 10 道")
    private Integer count = 3;

    /** 难度 1-5 */
    @Min(value = 1, message = "难度最小为 1")
    @Max(value = 5, message = "难度最大为 5")
    private Integer difficulty;

    /** 每题分值（仅用于提示模型输出的参考答案格式） */
    private BigDecimal score;
}
