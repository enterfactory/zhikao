package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目-知识点关联（P3 地基）：一题多知识点。
 * (question_id, kp_id) 唯一，重复挂同一知识点视为数据错误。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("question_knowledge")
public class QuestionKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    /** knowledge_point.id */
    private Long kpId;
}
