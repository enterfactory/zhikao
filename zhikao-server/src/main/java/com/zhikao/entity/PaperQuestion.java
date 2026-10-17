package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 试卷题目快照。
 * 组卷时把题目内容整体复制一份，题库后续修改（甚至删除）都不会影响已生成的试卷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("paper_question")
public class PaperQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    /** 源题目 id，便于溯源到题库 */
    private Long questionId;

    /** 题目顺序，从 1 开始 */
    private Integer sort;

    private Integer type;

    private String content;

    private String options;

    private String answer;

    private String analysis;

    private BigDecimal score;

    private Integer difficulty;

    private String knowledgePoint;
}
