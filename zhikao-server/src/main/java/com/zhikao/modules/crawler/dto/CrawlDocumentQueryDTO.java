package com.zhikao.modules.crawler.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档解析日志分页查询参数。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CrawlDocumentQueryDTO extends PageQuery {

    /** 按任务过滤，可空（任务列表点「查看解析日志」时带上） */
    private Long taskId;

    /** 按文档名/路径关键字过滤，可空 */
    private String keyword;

    /** true=只看在待审阶段没出过题的文档（解析日志的主要用途就是排查这类文档） */
    private Boolean unresolvedOnly;
}
