package com.zhikao.modules.exam.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考试分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamQueryDTO extends PageQuery {

    /** 关键字：匹配考试名称 */
    private String keyword;

    /** 班级 id */
    private Long classId;

    /** 0未开始 1进行中 2已结束 */
    private Integer status;

    /** 0草稿 1已发布 */
    private Integer published;
}
