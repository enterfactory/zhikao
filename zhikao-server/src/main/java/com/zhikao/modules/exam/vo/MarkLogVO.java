package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批改留痕视图：谁在什么时候把哪道题从多少分改成了多少分。
 */
@Data
public class MarkLogVO {

    private Long id;

    private Long examStudentId;

    private Long examAnswerId;

    /** 1首评 2改分 */
    private Integer action;

    private String actionName;

    private BigDecimal oldScore;

    private BigDecimal newScore;

    private String reason;

    private String operator;

    /** 学生姓名（便于教师直接看懂这条留痕属于谁） */
    private String studentName;

    /** 题号（第几题） */
    private Integer sort;

    /** 题干（列表里由服务端截断，避免 CLOB 全量回传） */
    private String questionContent;

    private LocalDateTime createTime;
}
