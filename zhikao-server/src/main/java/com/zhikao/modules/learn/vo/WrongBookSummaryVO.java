package com.zhikao.modules.learn.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 错题本统计概览。
 */
@Data
public class WrongBookSummaryVO {

    /** 错题总数 */
    private long total;

    /** 涉及的知识点数 */
    private int knowledgeCount;

    /** 最薄弱的知识点（正确率最低且低于阈值），无则为空 */
    private String weakestPoint;

    /** 该知识点正确率 */
    private BigDecimal weakestAccuracy;
}
