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
 * 题目：题库的最小单元，归属科目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 科目 id */
    private Long subjectId;

    /** 题型：1单选 2多选 3判断 4填空 5简答 */
    private Integer type;

    /** 题干 */
    private String content;

    /** 选项 JSON 字符串（如 {"A":"","B":""}），填空/简答为空 */
    private String options;

    /** 标准答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 难度 1-5 */
    private Integer difficulty;

    /** 知识点 */
    private String knowledgePoint;

    /** 默认分值 */
    private BigDecimal score;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
