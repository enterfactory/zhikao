package com.zhikao.modules.crawler.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待审/各阶段题目分页查询参数。
 * stage 为空表示不过滤阶段；否则按 crawl_question.stage 精确过滤（RAW/CLEANED/TAGGED/PROMOTED/REJECTED）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CrawlQuestionQueryDTO extends PageQuery {

    /** 阶段过滤 */
    private String stage;

    /** 科目过滤 */
    private Long subjectId;

    /** 学段过滤（小学/初中/高中） */
    private String grade;

    /**
     * 按来源文档过滤（crawl_question.raw_id）。
     * 解析日志里点「到题目预览看这些题」时带上：一份文档对应一道一道题，
     * 只有 raw_id 能把「这份文档认出了什么」和「待审列表」串起来。
     */
    private Long rawId;
}
