package com.zhikao.modules.exam.vo;

import lombok.Data;

/**
 * 作弊上报结果：把「系统拿这个上报做了什么」明确回给前端。
 *
 * <p>原来的接口什么都不返回，上报 1 次和 20 次在界面上完全一样——
 * 学生不知道自己是不是已经被记录，教师也不知道该不该介入。
 */
@Data
public class CheatReportVO {

    /** 当前累计次数 */
    private Integer count;

    /** 达到该次数开始给出警告 */
    private Integer warnThreshold;

    /** 达到该次数视为严重（默认只是打标记，是否强制收卷由配置决定） */
    private Integer seriousThreshold;

    /**
     * 本次处置：recorded（已记录） / warned（已达警告阈值） /
     * serious（已达严重阈值） / force_submitted（已达严重阈值并自动收卷）
     */
    private String action;

    /** 给学生的提示文案 */
    private String message;
}
