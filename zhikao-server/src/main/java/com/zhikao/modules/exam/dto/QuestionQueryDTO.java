package com.zhikao.modules.exam.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionQueryDTO extends PageQuery {

    /** 科目 id */
    private Long subjectId;

    /** 题型：1单选 2多选 3判断 4填空 5简答 */
    private Integer type;

    /** 难度 1-5 */
    private Integer difficulty;

    /** 关键字：同时匹配题干与知识点 */
    private String keyword;
}
