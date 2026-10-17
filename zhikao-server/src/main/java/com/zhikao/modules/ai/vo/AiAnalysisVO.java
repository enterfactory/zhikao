package com.zhikao.modules.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 考试错题分析结果。
 * 基础统计部分总是可用；「教学建议」一段在配置了大模型时由模型生成。
 */
@Data
public class AiAnalysisVO {

    private Long examId;

    private String examName;

    /** 各知识点掌握度（按正确率升序，最薄弱的排前面） */
    private List<KnowledgeStatVO> knowledgeStats = new ArrayList<>();

    /** 正确率低于阈值的知识点，作为薄弱项提示 */
    private List<String> weakPoints = new ArrayList<>();

    /** 教学建议：配置大模型时为模型生成，否则为基于统计的规则化提示 */
    private String suggestion;

    /** 教学建议是否由大模型生成 */
    private boolean aiGenerated;
}
