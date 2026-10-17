package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷：由题库抽题组成，题目内容以 paper_question 快照形式保存。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("paper")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paperName;

    private Long subjectId;

    /** 试卷总分（由各题分值累加得出） */
    private BigDecimal totalScore;

    /** 建议答题时长（分钟） */
    private Integer totalDuration;

    /** 组卷方式：1手动 2随机 */
    private Integer genType;

    /** 题目数量 */
    private Integer questionCount;

    /** 0草稿 1已发布（已发布不允许再改题目） */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;
}
