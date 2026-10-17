package com.zhikao.modules.exam.vo;

import lombok.Data;

/**
 * 相似题提示：AI 草稿入库前给教师看的「这道题可能已经有了」。
 */
@Data
public class QuestionSimilarVO {

    private Long id;

    private String content;

    private String typeName;

    private String knowledgePoint;

    /** 相似度 0-1，1 为完全一致 */
    private double similarity;

    /** 给教师的判断依据，如「题面重合度 82%」 */
    private String hint;
}
