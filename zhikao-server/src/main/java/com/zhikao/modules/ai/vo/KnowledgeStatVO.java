package com.zhikao.modules.ai.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 知识点掌握度统计（错题分析的基础数据）。
 * 数据全部来自答题明细的落库结果，直接由 SQL 聚合得出，不依赖大模型。
 */
@Data
public class KnowledgeStatVO {

    /** 学科分组，防止跨科目同名知识点合并。稳定知识点 ID 将由后续快照迁移引入。 */
    private Long subjectId;
    private Long knowledgePointId;

    private String subjectName;

    /** 知识点；未标注知识点的题目归为「未标注」 */
    private String knowledgePoint;

    /** 该知识点终评且已评分的作答总数；仍按历史快照中的标签文本分组。 */
    private Integer total;

    /** 其中答对的数量（主观题按教师批改结果判断满分即算对） */
    private Integer correctCount;

    /** 正确率（百分比，保留两位） */
    private BigDecimal accuracy;
}
