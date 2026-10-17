package com.zhikao.modules.exam.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperQueryDTO extends PageQuery {

    /** 科目 id */
    private Long subjectId;

    /** 组卷方式：1手动 2随机 */
    private Integer genType;

    /** 0草稿 1已发布 */
    private Integer status;

    /** 关键字：匹配试卷名称 */
    private String keyword;
}
